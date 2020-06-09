package org.example;

import org.apache.camel.component.jms.DestinationEndpoint;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsConstants;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.JmsMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsOperations;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

public class NewJmsConfig extends JmsConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(NewJmsConfig.class);

    public NewJmsConfig(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    /**
     * Creates a {@link JmsOperations} object used for one way messaging
     */
    @Override
    public JmsOperations createInOnlyTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination) {
        if (getJmsOperations() != null) {
            return getJmsOperations();
        }

        ConnectionFactory factory = getTemplateConnectionFactory();
        NewTemplate template = new NewTemplate(this, factory);
        template.setPubSubDomain(pubSubDomain);
        if (getDestinationResolver() != null) {
            template.setDestinationResolver(getDestinationResolver());
            if (endpoint instanceof DestinationEndpoint) {
                LOG.debug("You are overloading the destinationResolver property on a DestinationEndpoint; are you sure you want to do that?");
            }
        } else if (endpoint instanceof DestinationEndpoint) {
            DestinationEndpoint destinationEndpoint = (DestinationEndpoint) endpoint;
            template.setDestinationResolver(createDestinationResolver(destinationEndpoint));
        }
        template.setDefaultDestinationName(destination);

        template.setExplicitQosEnabled(isExplicitQosEnabled());

        // have to use one or the other.. doesn't make sense to use both
        if (getDeliveryMode() != null) {
            template.setDeliveryMode(getDeliveryMode());
        } else {
            template.setDeliveryPersistent(isDeliveryPersistent());
        }

        if (getMessageConverter() != null) {
            template.setMessageConverter(getMessageConverter());
        }
        template.setMessageIdEnabled(isMessageIdEnabled());
        template.setMessageTimestampEnabled(isMessageTimestampEnabled());
        if (getPriority() >= 0) {
            template.setPriority(getPriority());
        }
        template.setPubSubNoLocal(isPubSubNoLocal());
        // only set TTL if we have a positive value and it has not been disabled
        if (getTimeToLive() >= 0 && !isDisableTimeToLive()) {
            template.setTimeToLive(getTimeToLive());
        }

        template.setSessionTransacted(isTransacted());
        if (isTransacted()) {
            template.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            // This is here for completeness, but the template should not get
            // used for receiving messages.
            if (getAcknowledgementMode() >= 0) {
                template.setSessionAcknowledgeMode(getAcknowledgementMode());
            } else if (getAcknowledgementModeName() != null) {
                template.setSessionAcknowledgeModeName(getAcknowledgementModeName());
            }
        }
        return template;
    }

    static class NewTemplate extends CamelJmsTemplate{
        private JmsConfiguration config;

        public NewTemplate(JmsConfiguration config, ConnectionFactory connectionFactory) {
            super(config, connectionFactory);
            this.config = config;
        }

        @Override
        protected void doSend(MessageProducer producer, Message message) throws JMSException {
            // Set the delivery delay (in milliseconds) in any JMS header and use it here. For example, the deliveryDelay header is used here
            try {
                String delayObject = message.getStringProperty("deliveryDelay");
                long delay = 0;
                if ( delayObject != null && delayObject.length() > 0 && ( delay = Long.parseLong(delayObject)) > 0) {
                        producer.setDeliveryDelay(delay);
                }
            } catch (Exception e){
                LOG.error("Exception occurred while setting the delivery delay",e);
            }
            if (config.isPreserveMessageQos()) {
                long ttl = message.getJMSExpiration();
                if (ttl != 0) {
                    ttl = ttl - System.currentTimeMillis();
                    // Message had expired.. so set the ttl as small as possible
                    if (ttl <= 0) {
                        ttl = 1;
                    }
                }

                int priority = message.getJMSPriority();
                if (priority < 0 || priority > 9) {
                    // use priority from endpoint if not provided on message with a valid range
                    priority = this.getPriority();
                }

                // if a delivery mode was set as a JMS header then we have used a temporary
                // property to store it - CamelJMSDeliveryMode. Otherwise we could not keep
                // track whether it was set or not as getJMSDeliveryMode() will default return 1 regardless
                // if it was set or not, so we can never tell if end user provided it in a header
                int deliveryMode;
                if (JmsMessageHelper.hasProperty(message, JmsConstants.JMS_DELIVERY_MODE)) {
                    deliveryMode = message.getIntProperty(JmsConstants.JMS_DELIVERY_MODE);
                    // remove the temporary property
                    JmsMessageHelper.removeJmsProperty(message, JmsConstants.JMS_DELIVERY_MODE);
                } else {
                    // use the existing delivery mode from the message
                    deliveryMode = message.getJMSDeliveryMode();
                }

                // need to log just before so the message is 100% correct when logged
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending JMS message to: {} with message: {}", producer.getDestination(), message);
                }
                producer.send(message, deliveryMode, priority, ttl);
            } else {
                // need to log just before so the message is 100% correct when logged
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending JMS message to: {} with message: {}", producer.getDestination(), message);
                }
                super.doSend(producer, message);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Sent JMS message to: {} with message: {}", producer.getDestination(), message);
                }
            }
        }
    }
}


