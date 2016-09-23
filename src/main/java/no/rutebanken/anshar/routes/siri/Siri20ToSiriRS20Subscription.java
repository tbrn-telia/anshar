package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.subscription.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.xml.Namespaces;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.http.common.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


public class Siri20ToSiriRS20Subscription extends SiriSubscriptionRouteBuilder {

    private static Logger logger = LoggerFactory.getLogger(Siri20ToSiriRS20Subscription.class);

    public Siri20ToSiriRS20Subscription(SubscriptionSetup subscriptionSetup) {

        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        Map<String, String> urlMap = subscriptionSetup.getUrlMap();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        //Start subscription
        from("direct:start" + uniqueRouteName)
                .bean(this, "marshalSiriSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("log:sent request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("http4://" + urlMap.get(RequestType.SUBSCRIBE))
                .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                    if ("200".equals(responseCode)) {
                        logger.info("SubscriptionResponse OK - Async response performs actual registration");
                        SubscriptionManager.addPendingSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
                    }

                })
        ;

        //Check status-request checks the server status - NOT the subscription
        if (subscriptionSetup.isActive() & urlMap.get(RequestType.CHECK_STATUS) != null) {
            from("quartz2://checkstatus" + uniqueRouteName + "?fireNow=true&trigger.repeatInterval=" + subscriptionSetup.getHeartbeatInterval().toMillis())
                    .bean(this, "marshalSiriCheckStatusRequest", false)
                    .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                    .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                    .to("http4://" + urlMap.get(RequestType.CHECK_STATUS))
                    .process(p -> {

                        String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                        if ("200".equals(responseCode)) {
                            logger.trace("CheckStatus OK - Remote service is up [{}]", subscriptionSetup.buildUrl());
                        } else {
                            logger.info("CheckStatus NOT OK - Remote service is down [{}]", subscriptionSetup.buildUrl());
                        }

                    })
            ;
        }

        //Cancel subscription
        from("direct:cancel" + uniqueRouteName)
                .bean(this, "marshalSiriTerminateSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("http4://" + urlMap.get(RequestType.DELETE_SUBSCRIPTION))
                .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                    logger.info("TerminateSubscriptionResponse {}", responseCode);
                    SubscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId());

                });


        initShedulerRoute();

    }

}