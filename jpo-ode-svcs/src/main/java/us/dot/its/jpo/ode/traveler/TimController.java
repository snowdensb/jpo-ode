package us.dot.its.jpo.ode.traveler;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;

import us.dot.its.jpo.ode.OdeProperties;
import us.dot.its.jpo.ode.dds.DdsClient.DdsClientException;
import us.dot.its.jpo.ode.dds.DdsDepositor;
import us.dot.its.jpo.ode.dds.DdsRequestManager.DdsRequestManagerException;
import us.dot.its.jpo.ode.dds.DdsStatusMessage;
import us.dot.its.jpo.ode.model.OdeMsgMetadata;
import us.dot.its.jpo.ode.model.OdeMsgPayload;
import us.dot.its.jpo.ode.model.OdeObject;
import us.dot.its.jpo.ode.model.OdeTravelerInformationData;
import us.dot.its.jpo.ode.model.TravelerInputData;
import us.dot.its.jpo.ode.plugin.RoadSideUnit.RSU;
import us.dot.its.jpo.ode.plugin.j2735.oss.OssTravelerMessageBuilder;
import us.dot.its.jpo.ode.snmp.SNMP;
import us.dot.its.jpo.ode.snmp.SnmpSession;
import us.dot.its.jpo.ode.traveler.TimPduCreator.TimPduCreatorException;
import us.dot.its.jpo.ode.util.JsonUtils;
import us.dot.its.jpo.ode.wrapper.MessageProducer;
import us.dot.its.jpo.ode.wrapper.WebSocketEndpoint.WebSocketException;
import us.dot.its.jpo.ode.wrapper.serdes.OdeTravelerInformationMessageSerializer;

@Controller
public class TimController {

   private static Logger logger = LoggerFactory.getLogger(TimController.class);

   private OdeProperties odeProperties;

   private DdsDepositor<DdsStatusMessage> depositor;

   private MessageProducer<String, OdeObject> messageProducer;

   @Autowired
   public TimController(OdeProperties odeProperties) {
      super();
      this.odeProperties = odeProperties;
      
      this.messageProducer = new MessageProducer<>(odeProperties.getKafkaBrokers(), odeProperties.getKafkaProducerType(),
            null, OdeTravelerInformationMessageSerializer.class.getName());

      try {
         depositor = new DdsDepositor<>(this.odeProperties);
      } catch (Exception e) {
         logger.error("Error starting SDW depositor", e);
      }
   }
   
   /**
    * Checks given RSU for all TIMs set
    * 
    * @param jsonString
    *           Request body containing RSU info
    * @return list of occupied TIM slots on RSU
    */
   @ResponseBody
   @CrossOrigin
   @RequestMapping(value = "/tim/query", method = RequestMethod.POST)
   public ResponseEntity<String> asyncQueryForTims(@RequestBody String jsonString) { // NOSONAR

      if (null == jsonString) {
         logger.error("Empty request.");
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Empty request");
      }

      ConcurrentHashMap<Integer, Boolean> resultTable = new ConcurrentHashMap<>();
      RSU queryTarget = (RSU) JsonUtils.fromJson(jsonString, RSU.class);

      Address addr = GenericAddress.parse(queryTarget.getRsuTarget() + "/161");
      
      // Create a "target" to which a request is sent
      UserTarget target = new UserTarget();
      target.setAddress(addr);
      target.setRetries(queryTarget.getRsuRetries());
      target.setTimeout(queryTarget.getRsuTimeout());
      target.setVersion(SnmpConstants.version3);
      target.setSecurityLevel(SecurityLevel.AUTH_NOPRIV);
      target.setSecurityName(new OctetString(queryTarget.getRsuUsername()));

      // Set up the UDP transport mapping over which requests are sent
      TransportMapping transport = null;
      try {
         transport = new DefaultUdpTransportMapping();
         transport.listen();
      } catch (IOException e) {
         logger.error("Failed to create UDP transport mapping: {}", e);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\":\"" + e + "\"}");
      }

      // Instantiate the SNMP instance
      Snmp snmp = new Snmp(transport);

      // Register the security options and create an SNMP "user"
      USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
      SecurityModels.getInstance().addSecurityModel(usm);
      snmp.getUSM().addUser(new OctetString(queryTarget.getRsuUsername()), new UsmUser(new OctetString(queryTarget.getRsuUsername()),
            AuthMD5.ID, new OctetString(queryTarget.getRsuPassword()), null, null));
      

      // Repeatedly query the RSU to establish set rows
      ExecutorService es = Executors.newFixedThreadPool(odeProperties.getRsuSrmSlots());
      List<Callable<Object>> queryThreadList = new ArrayList<Callable<Object>>();
      
      for (int i = 0; i < odeProperties.getRsuSrmSlots(); i++) {
         PDU pdu = new ScopedPDU();
         pdu.add(new VariableBinding(new OID("1.0.15628.4.1.4.1.11.".concat(Integer.toString(i)))));
         pdu.setType(PDU.GET);
         
         logger.info("Querying index {}", i);
         
         queryThreadList.add(Executors.callable(new QueryThread(snmp, pdu, target, resultTable, i)));
      }
         
      try {
         es.invokeAll(queryThreadList);
      } catch (InterruptedException e) { //NOSONAR
         logger.error("Error submitting query threads for execution.", e);
         es.shutdownNow();
      }
      
      try {
         snmp.close();
      } catch (IOException e) {
         logger.error("Error closing SNMP session.", e);
      }
      
      logger.info("TIM query successful: {}", resultTable.keySet());
      return ResponseEntity.status(HttpStatus.OK).body("{\"indicies_set\",".concat(resultTable.keySet().toString()).concat("}"));
   }

   @ResponseBody
   @CrossOrigin
   @RequestMapping(value = "/tim", method = RequestMethod.DELETE)
   public ResponseEntity<String> deleteTim(@RequestBody String jsonString, @RequestParam(value="index", required=true) Integer index) { // NOSONAR
      
      if (null == jsonString) {
         logger.error("Empty request");
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Empty request.");
      }

      RSU queryTarget = (RSU) JsonUtils.fromJson(jsonString, RSU.class);

      logger.info("TIM delete call, RSU info {}", queryTarget);
      
      SnmpSession ss = null;
      try {
         ss = new SnmpSession(queryTarget);
      } catch (IOException e) {
         logger.error("Error creating TIM delete SNMP session", e);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
      } catch (NullPointerException e) {
         logger.error("TIM query error, malformed JSON", e);
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed JSON");
      }

      PDU pdu = new ScopedPDU();
      pdu.add(new VariableBinding(new OID("1.0.15628.4.1.4.1.11.".concat(Integer.toString(index))), new Integer32(6)));
      pdu.setType(PDU.SET);

      ResponseEvent rsuResponse = null;
      try {
         rsuResponse = ss.set(pdu, ss.getSnmp(), ss.getTarget(), false);
      } catch (IOException e) {
         logger.error("Error sending TIM query PDU to RSU", e);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
      }
      
      // Try to explain common errors
      HttpStatus returnCode = null;
      String bodyMsg = "";
      if (null == rsuResponse || null == rsuResponse.getResponse()) {
         // Timeout
         returnCode = HttpStatus.REQUEST_TIMEOUT;
         bodyMsg = "Timeout";
      } else if (rsuResponse.getResponse().getErrorStatus() == 0) {
         // Success
         returnCode = HttpStatus.OK;
         bodyMsg = "{\"deleted_msg\":\"".concat(Integer.toString(index)).concat("\"}");
      } else if (rsuResponse.getResponse().getErrorStatus() == 12) {
         // Message previously deleted or doesn't exist
         returnCode = HttpStatus.BAD_REQUEST;
         bodyMsg = "No message at index ".concat(Integer.toString(index));
      } else if (rsuResponse.getResponse().getErrorStatus() == 10) {
         // Invalid index
         returnCode = HttpStatus.BAD_REQUEST;
         bodyMsg = "Invalid index ".concat(Integer.toString(index));
      } else {
         // Misc error
         returnCode = HttpStatus.BAD_REQUEST;
         bodyMsg = rsuResponse.getResponse().getErrorStatusText();
      }
      
      logger.info("Delete call response code: {}, message: {}", returnCode, bodyMsg);

      return ResponseEntity.status(returnCode).body(bodyMsg);
   }

   /**
    * Deposit a TIM
    * 
    * @param jsonString
    *           TIM in JSON
    * @return list of success/failures
    */
   @ResponseBody
   @RequestMapping(value = "/tim", method = RequestMethod.POST, produces = "application/json")
   @CrossOrigin
   public ResponseEntity<String> timMessage(@RequestBody String jsonString) {
      
      // Check empty
      if (null == jsonString || jsonString.isEmpty()) {
         String errMsg = "Empty request.";
         logger.error(errMsg);
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errMsg);
      }

      // Convert JSON to POJO
      TravelerInputData travelerinputData = null;
      try {
         travelerinputData = (TravelerInputData) JsonUtils.fromJson(jsonString, TravelerInputData.class);

         String jsonFqResult = travelerinputData.toJson(true);
         logger.debug("J2735TravelerInputData: {}", jsonFqResult);

      } catch (Exception e) {
         String errMsg = "Malformed JSON.";
         logger.error(errMsg, e);
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errMsg);
      }
      
      // Add metadata to message and publish to kafka
      OdeMsgPayload timDataPayload = new OdeMsgPayload(travelerinputData.getTim());
      OdeMsgMetadata timMetadata = new OdeMsgMetadata(timDataPayload);
      OdeTravelerInformationData odeTimData = new OdeTravelerInformationData(timMetadata, timDataPayload);
      messageProducer.send(odeProperties.getKafkaTopicOdeTimPojo(), null, odeTimData);

      // Craft ASN-encodable TIM
      OssTravelerMessageBuilder builder = new OssTravelerMessageBuilder();
      try {
         builder.buildTravelerInformation(travelerinputData.getTim());
      } catch (Exception e) {
         String errMsg = "Request does not match schema: " + e.getMessage();
         logger.error(errMsg, e);
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errMsg);
      }

      // Encode TIM
      String rsuSRMPayload = null;
      try {
         rsuSRMPayload = builder.encodeTravelerInformationToHex();
         logger.debug("Encoded Hex TIM: {}", rsuSRMPayload);
      } catch (Exception e) {
         String errMsg = "Failed to encode TIM.";
         logger.error(errMsg, e);
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
      }         
      
      // Send TIMs and record results
      HashMap<String, ResponseEntity<String>> responseList = new HashMap<>();

      for (RSU curRsu : travelerinputData.getRsus()) {

         ResponseEvent rsuResponse = null;
         ResponseEntity<String> httpResponseStatus = null;
         
         try {
            rsuResponse = createAndSend(travelerinputData.getSnmp(), curRsu, travelerinputData.getTim().getIndex(),
                  rsuSRMPayload);
            
            if (null == rsuResponse || null == rsuResponse.getResponse()) {
               // Timeout
               httpResponseStatus = ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Timeout.");
            } else if (rsuResponse.getResponse().getErrorStatus() == 0) {
               // Success
               httpResponseStatus = ResponseEntity.status(HttpStatus.OK).body("Success.");
            } else if (rsuResponse.getResponse().getErrorStatus() == 5) {
               // Error, message already exists
               httpResponseStatus = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Message already exists at ".concat(Integer.toString(travelerinputData.getTim().getIndex())));
            } else {
               // Misc error
               httpResponseStatus = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error code " + rsuResponse.getResponse().getErrorStatus() + " " + rsuResponse.getResponse().getErrorStatusText());
            }

         } catch (Exception e) {
            logger.error("Exception caught in TIM deposit loop.", e);
            httpResponseStatus = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getClass().getName());
         }
         
         responseList.put(curRsu.getRsuTarget(), httpResponseStatus);
      }

      // Deposit to DDS
      String ddsMessage = "";
      try {
         depositToDDS(travelerinputData, rsuSRMPayload);
         ddsMessage = "\"dds_deposit\":{\"success\":\"true\"}";
         logger.info("DDS deposit successful.");
      } catch (Exception e) {
         ddsMessage = "\"dds_deposit\":{\"success\":\"false\"}";
         logger.error("Error on DDS deposit.", e);
      }
      
      // Craft a JSON response
      
      String rsuMessages = "[";
      for (Map.Entry<String, ResponseEntity<String>> entry : responseList.entrySet()) {
         
            String curKey = entry.getKey();
            ResponseEntity<String> curEnt = entry.getValue();
         
         if ( !("[".equals(rsuMessages)) ) {
         // Add a comma after every subsequent message
            rsuMessages = rsuMessages.concat(",");
         }
         
         String curMsg;
         if (curEnt.getStatusCode() == HttpStatus.OK) {
            curMsg = "{\"target\":\"" + curKey + "\",\"success\":\"true\",\"message\":\"" + curEnt.getBody() + "\"}";
         } else {
            curMsg = "{\"target\":\"" + curKey + "\",\"success\":\"false\",\"error\":\"" + curEnt.getBody() + "\"}";
         }
         
         rsuMessages = rsuMessages.concat(curMsg);
      }
      rsuMessages = rsuMessages.concat("]");
      
      String responseMessage = "{\"rsu_responses\":" + rsuMessages + "," + ddsMessage + "}";
      
      logger.info("TIM deposit response {}", responseMessage);
      
      return ResponseEntity.status(HttpStatus.OK).body(responseMessage);
   }

   private void depositToDDS(TravelerInputData travelerinputData, String rsuSRMPayload)
         throws ParseException, DdsRequestManagerException, DdsClientException, WebSocketException,
         EncodeFailedException, EncodeNotSupportedException {
      // Step 4 - Step Deposit TIM to SDW if sdw element exists
      if (travelerinputData.getSdw() != null) {
         AsdMessage message = new AsdMessage(travelerinputData.getSnmp().getDeliverystart(),
               travelerinputData.getSnmp().getDeliverystop(), rsuSRMPayload,
               travelerinputData.getSdw().getServiceRegion(), travelerinputData.getSdw().getTtl());
         depositor.deposit(message);
      }
   }

   /**
    * Create an SNMP session given the values in
    * 
    * @param tim
    *           - The TIM parameters (payload, channel, mode, etc)
    * @param props
    *           - The SNMP properties (ip, username, password, etc)
    * @return ResponseEvent
    * @throws TimPduCreatorException
    * @throws IOException
    */
   public static ResponseEvent createAndSend(SNMP snmp, RSU rsu, int index, String payload)
         throws IOException, TimPduCreatorException {

      SnmpSession session = new SnmpSession(rsu);

      // Send the PDU
      ResponseEvent response = null;
      ScopedPDU pdu = TimPduCreator.createPDU(snmp, payload, index);
      response = session.set(pdu, session.getSnmp(), session.getTarget(), false);
      return response;
   }

}