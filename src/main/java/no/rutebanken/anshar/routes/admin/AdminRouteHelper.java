/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.admin;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdminRouteHelper {

    @Autowired
    private SubscriptionManager subscriptionManager;


    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;


    public void flushDataFromSubscription(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);
        if (subscriptionSetup != null) {
            flushData(subscriptionSetup.getDatasetId(), subscriptionSetup.getSubscriptionType().name());
        }
    }

    private void flushData(String datasetId, String dataType) {
        switch (dataType) {
            case "ESTIMATED_TIMETABLE":
                estimatedTimetables.clearAllByDatasetId(datasetId);
                break;
            case "VEHICLE_MONITORING":
                vehicleActivities.clearAllByDatasetId(datasetId);
                break;
            case "SITUATION_EXCHANGE":
                situations.clearAllByDatasetId(datasetId);
                break;
        }
    }
}
