package us.dot.its.jpo.ode.coder;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import us.dot.its.jpo.ode.OdeProperties;
import us.dot.its.jpo.ode.SerializableMessageProducerPool;
import us.dot.its.jpo.ode.plugin.PluginFactory;
import us.dot.its.jpo.ode.plugin.asn1.J2735Plugin;
import us.dot.its.jpo.ode.wrapper.MessageProducer;

@RunWith(JMockit.class)
public class SecondAbstractCoderTest {

    @Tested
    AbstractStreamDecoderPublisher testAbstractCoder;
    @Injectable
    OdeProperties mockOdeProperties;

    @Mocked
    J2735Plugin mockAsn1Plugin;

    @Test @Ignore // TODO having getLogger method deprecates abstract class tests
    public void testPublishJson(@Injectable final PluginFactory mockPluginFactory,
            @Mocked final MessageProducer<?, ?> mockMessageProducer,
            @Mocked final SerializableMessageProducerPool<?, ?> mockSerializableMessageProducerPool) {

        testAbstractCoder.publish("{testJsonString}");
    }
}
