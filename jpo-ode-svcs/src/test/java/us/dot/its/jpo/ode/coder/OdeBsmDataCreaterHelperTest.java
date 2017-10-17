package us.dot.its.jpo.ode.coder;

import org.junit.Test;

import gov.usdot.cv.security.msg.IEEE1609p2Message;
import mockit.Capturing;
import us.dot.its.jpo.ode.importer.parser.BsmFileParser;
import us.dot.its.jpo.ode.model.OdeBsmData;
import us.dot.its.jpo.ode.model.SerialId;
import us.dot.its.jpo.ode.plugin.j2735.J2735Bsm;

public class OdeBsmDataCreaterHelperTest {
   @Capturing
   J2735Bsm capturingJ2735Bsm;
   @Capturing
   IEEE1609p2Message capturingIEEE1609p2Message;
   @Capturing
   BsmFileParser capturingBsmFileParser;
   @Capturing
   SerialId capturingSerialId;
   
   
   @Test
   public void notNullTest() {
      
      OdeBsmData testingOde = new OdeBsmData();
      OdeBsmDataCreatorHelper testOdbBsmDataCreaterHelper = new OdeBsmDataCreatorHelper();

      testingOde = testOdbBsmDataCreaterHelper.createOdeBsmData(capturingJ2735Bsm, capturingIEEE1609p2Message, capturingBsmFileParser, capturingSerialId);

      
   }
   @Test
   public void nullTest() {
      capturingIEEE1609p2Message = null;
      OdeBsmData testingOde = new OdeBsmData();
      OdeBsmDataCreatorHelper testOdbBsmDataCreaterHelper = new OdeBsmDataCreatorHelper();

      testingOde = testOdbBsmDataCreaterHelper.createOdeBsmData(capturingJ2735Bsm, capturingIEEE1609p2Message, capturingBsmFileParser, capturingSerialId);

   }


}
