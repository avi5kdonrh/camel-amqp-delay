package org.example;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.qpid.proton.amqp.Symbol;

public class OldPro implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
     exchange.getIn().setHeader("x-opt-delivery-delay",150000L);

     exchange.getIn().setHeader(Symbol.valueOf("x-opt-delivery-time").toString(),System.currentTimeMillis()+150000L);

      //exchange.getIn().setHeader("_AMQ_SCHED_DELIVERY",System.currentTimeMillis()+150000);
    }
}
