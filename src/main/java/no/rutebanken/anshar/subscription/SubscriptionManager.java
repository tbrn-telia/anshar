package no.rutebanken.anshar.subscription;


import com.google.common.base.Preconditions;
import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubscriptionManager {

    final int HEALTHCHECK_INTERVAL_FACTOR = 5;
    private Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    @Autowired
    @Qualifier("getActiveSubscriptionsMap")
    private IMap<String, SubscriptionSetup> activeSubscriptions;

    @Autowired
    @Qualifier("getPendingSubscriptionsMap")
    private IMap<String, SubscriptionSetup> pendingSubscriptions;

    @Autowired
    @Qualifier("getLastActivityMap")
    private IMap<String, java.time.Instant> lastActivity;

    @Autowired
    @Qualifier("getActivatedTimestampMap")
    private IMap<String, java.time.Instant> activatedTimestamp;

    @Autowired
    private IMap<String, Integer> hitcount;

    @Autowired
    private IMap<String, BigInteger> objectCounter;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Autowired
    private Situations sx;
    @Autowired
    private EstimatedTimetables et;
    @Autowired
    private ProductionTimetables pt;
    @Autowired
    private VehicleActivities vm;

    public void addSubscription(String subscriptionId, SubscriptionSetup setup) {
        Preconditions.checkState(!pendingSubscriptions.containsKey(subscriptionId), "Subscription already exists (pending)");
        Preconditions.checkState(!activeSubscriptions.containsKey(subscriptionId), "Subscription already exists (active)");

        activeSubscriptions.put(subscriptionId, setup);
        logger.trace("Added subscription {}", setup);
        activatedTimestamp.put(subscriptionId, Instant.now());
        logStats();
    }

    public boolean removeSubscription(String subscriptionId) {
        return removeSubscription(subscriptionId, false);
    }

    public boolean removeSubscription(String subscriptionId, boolean force) {
        SubscriptionSetup setup = activeSubscriptions.remove(subscriptionId);
        if (setup == null) {
            setup = pendingSubscriptions.remove(subscriptionId);
        }

        boolean found = (setup != null);

        if (force) {
            logger.info("Completely deleting subscription by request.");
            pendingSubscriptions.remove(subscriptionId);
            activatedTimestamp.remove(subscriptionId);
            lastActivity.remove(subscriptionId);
            hitcount.remove(subscriptionId);
            objectCounter.remove(subscriptionId);
        } else if (found) {
            addPendingSubscription(subscriptionId, setup);
        }

        logStats();

        logger.info("Removed subscription {}, found: {}", (setup !=null ? setup.toString():subscriptionId), found);
        return found;
    }

    public boolean touchSubscription(String subscriptionId) {
        SubscriptionSetup setup = activeSubscriptions.get(subscriptionId);
        hit(subscriptionId);

        boolean success = (setup != null);

        if (!success) {
            // Handling race conditions caused by async responses
            success = activatePendingSubscription(subscriptionId);
        }

        logger.trace("Touched subscription {}, success:{}", setup, success);
        if (success) {
            lastActivity.put(subscriptionId, Instant.now());
        }

        logStats();
        return success;
    }

    /**
     * Touches subscription if reported serviceStartedTime is BEFORE last activity.
     * If not, subscription is removed to trigger reestablishing subscription
     * @param subscriptionId
     * @param serviceStartedTime
     * @return
     */
    public boolean touchSubscription(String subscriptionId, ZonedDateTime serviceStartedTime) {
        SubscriptionSetup setup = activeSubscriptions.get(subscriptionId);
        if (setup != null && serviceStartedTime != null) {
            if (lastActivity.get(subscriptionId).isAfter(serviceStartedTime.toInstant())) {
                return touchSubscription(subscriptionId);
            } else {
                logger.info("Remote service has been restarted, reestablishing subscription [{}]", subscriptionId);
                //Setting 'last activity' to longer ago than healthcheck accepts
                lastActivity.put(subscriptionId, Instant.now().minusSeconds((HEALTHCHECK_INTERVAL_FACTOR+1) * setup.getHeartbeatInterval().getSeconds()));
            }
        }
        return false;
    }

    private void logStats() {
        String stats = "Active subscriptions: " + activeSubscriptions.size() + ", Pending subscriptions: " + pendingSubscriptions.size();
        logger.debug(stats);
    }

    public SubscriptionSetup get(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = activeSubscriptions.get(subscriptionId);

        if (subscriptionSetup == null) {
            //Pending subscriptions are also "valid"
            subscriptionSetup = pendingSubscriptions.get(subscriptionId);
        }
        return subscriptionSetup;
    }

    private void hit(String subscriptionId) {
        int counter = (hitcount.get(subscriptionId) != null ? hitcount.get(subscriptionId):0);
        hitcount.put(subscriptionId, counter+1);
    }

    public void incrementObjectCounter(SubscriptionSetup subscriptionSetup, int size) {

        String subscriptionId = subscriptionSetup.getSubscriptionId();
        if (subscriptionId != null) {
            BigInteger counter = (objectCounter.get(subscriptionId) != null ? objectCounter.get(subscriptionId) : new BigInteger("0"));
            objectCounter.put(subscriptionId, counter.add(BigInteger.valueOf(size)));
        }
    }

    public void addPendingSubscription(String subscriptionId, SubscriptionSetup subscriptionSetup) {
        activatedTimestamp.remove(subscriptionId);
        activeSubscriptions.remove(subscriptionId);
        pendingSubscriptions.put(subscriptionId, subscriptionSetup);
//        lastActivity.put(subscriptionId, Instant.now());

        logger.info("Added pending subscription {}", subscriptionSetup.toString());
    }

    public boolean isPendingSubscription(String subscriptionId) {
        return pendingSubscriptions.containsKey(subscriptionId);
    }
    public boolean isActiveSubscription(String subscriptionId) {
        return activeSubscriptions.containsKey(subscriptionId);
    }

    public boolean activatePendingSubscription(String subscriptionId) {
        if (isPendingSubscription(subscriptionId)) {
            SubscriptionSetup setup = pendingSubscriptions.remove(subscriptionId);
            addSubscription(subscriptionId, setup);
            lastActivity.put(subscriptionId, Instant.now());
            logger.info("Pending subscription {} activated", setup.toString());
            return true;
        }
        if (isActiveSubscription(subscriptionId)) {
            lastActivity.put(subscriptionId, Instant.now());
            logger.info("Pending subscription {} already activated", activeSubscriptions.get(subscriptionId));
            return true;
        }

        logger.warn("Pending subscriptionId [{}] NOT found", subscriptionId);
        return false;
    }

    public Boolean isSubscriptionHealthy(String subscriptionId) {
        Instant instant = lastActivity.get(subscriptionId);

        if (instant == null) {
            //Subscription has not had any activity, and may not have been started yet - flag as healthy
            return true;
        }

        logger.trace("SubscriptionId [{}], last activity {}.", subscriptionId, instant);

        SubscriptionSetup activeSubscription = activeSubscriptions.get(subscriptionId);
        if (activeSubscription != null) {
            long allowedInterval = activeSubscription.getHeartbeatInterval().toMillis() * HEALTHCHECK_INTERVAL_FACTOR;
            if (instant.isBefore(Instant.now().minusMillis(allowedInterval))) {
                //Subscription exists, but there has not been any activity recently
                return false;
            }

            if (activeSubscription.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE)) {
                //Only actual subscriptions have an expiration - NOT request/response-"subscriptions"

                //If active subscription has existed longer than "initial subscription duration" - restart
                if (activatedTimestamp.get(subscriptionId) != null && activatedTimestamp.get(subscriptionId)
                        .plusSeconds(
                                activeSubscription.getDurationOfSubscription().getSeconds()
                        ).isBefore(Instant.now())) {
                    logger.info("Subscription  [{}] has lasted longer than initial subscription duration ", activeSubscription.toString());
                    return false;
                }
            }

        }

        SubscriptionSetup pendingSubscription = pendingSubscriptions.get(subscriptionId);
        if (pendingSubscription != null) {
            long allowedInterval = pendingSubscription.getHeartbeatInterval().toMillis() * HEALTHCHECK_INTERVAL_FACTOR;
            if (instant.isBefore(Instant.now().minusMillis(allowedInterval))) {
                logger.debug("Subscription {} never activated.", pendingSubscription.toString());
                //Subscription created, but never received - reestablish subscription
                return false;
            }
        }

        return true;
    }

    public boolean isSubscriptionRegistered(String subscriptionId) {

        if (activeSubscriptions.containsKey(subscriptionId) |
                pendingSubscriptions.containsKey(subscriptionId)) {
            return true;
        }
        //Subscription not registered - trigger start
        return false;
    }

    public JSONObject buildStats() {
        JSONObject result = new JSONObject();
        JSONArray stats = new JSONArray();
        stats.addAll(activeSubscriptions.keySet().stream()
                .map(key -> getJsonObject(activeSubscriptions.get(key), "active"))
                .filter(json -> json != null)
                .collect(Collectors.toList()));
        stats.addAll(pendingSubscriptions.keySet().stream()
                .map(key -> getJsonObject(pendingSubscriptions.get(key), "pending"))
                .filter(json -> json != null)
                .collect(Collectors.toList()));

        result.put("subscriptions", stats);

        result.put("serverStarted", formatTimestamp(siriObjectFactory.serverStartTime));
        JSONObject count = new JSONObject();
        count.put("sx", sx.getSize());
        count.put("et", et.getSize());
        count.put("vm", vm.getSize());
        count.put("pt", pt.getSize());

        result.put("elements", count);

        return result;
    }

    private JSONObject getJsonObject(SubscriptionSetup setup, String status) {
        if (setup == null) {
            return null;
        }
        JSONObject obj = setup.toJSON();
        obj.put("activated",formatTimestamp(activatedTimestamp.get(setup.getSubscriptionId())));
        obj.put("lastActivity",""+formatTimestamp(lastActivity.get(setup.getSubscriptionId())));
        if (!setup.isActive()) {
            obj.put("status", "deactivated");
            obj.put("healthy",null);
        } else {
            obj.put("status", status);
            obj.put("healthy",isSubscriptionHealthy(setup.getSubscriptionId()));
        }
        obj.put("hitcount",hitcount.get(setup.getSubscriptionId()));
        obj.put("objectcount",objectCounter.get(setup.getSubscriptionId()));

        JSONObject urllist = new JSONObject();
        for (RequestType s : setup.getUrlMap().keySet()) {
            urllist.put(s.name(), setup.getUrlMap().get(s));
        }
        obj.put("urllist", urllist);

        return obj;
    }

    private String formatTimestamp(Instant instant) {
        if (instant != null) {
            return formatter.format(instant);
        }
        return "";
    }

    public Map<String, SubscriptionSetup> getActiveSubscriptions() {
        return activeSubscriptions;
    }

    public Map<String, SubscriptionSetup> getPendingSubscriptions() {
        return pendingSubscriptions;
    }

    public SubscriptionSetup getSubscriptionById(long internalId) {
        for (SubscriptionSetup setup : activeSubscriptions.values()) {
            if (setup.getInternalId() == internalId) {
                return setup;
            }
        }
        for (SubscriptionSetup setup : pendingSubscriptions.values()) {
            if (setup.getInternalId() == internalId) {
                return setup;
            }
        }
        return null;
    }
}
