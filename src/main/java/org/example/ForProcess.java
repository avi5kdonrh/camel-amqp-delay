package org.example;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.jms.JmsQueueEndpoint;

public class ForProcess implements Processor {
    @EndpointInject(uri = "amqp:queue:ABC?jmsKeyFormatStrategy=#noformat")
    JmsQueueEndpoint producerTemplate;
    @Override
    public void process(Exchange exchange) throws Exception {
        NewJmsConfig newJmsConfig = new NewJmsConfig();
        newJmsConfig.setDeliveryDelay(150000);
        newJmsConfig.setConnectionFactory(producerTemplate.getConnectionFactory());
        producerTemplate.setConfiguration(newJmsConfig);
        Producer jmsProducer = producerTemplate.createProducer();
        jmsProducer.start();
        jmsProducer.process(exchange);
        jmsProducer.stop();

    }
}
