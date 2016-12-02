package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.App;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.VehicleRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class EstimatedTimetablesTest {

    @Autowired
    EstimatedTimetables estimatedTimetables;

    @Before
    public void setup() {
        estimatedTimetables.getAll().clear();
    }

    @Test
    public void testAddNull() {
        int previousSize = estimatedTimetables.getAll().size();
        estimatedTimetables.add("test", null);

        assertEquals(previousSize, estimatedTimetables.getAll().size());
    }

    @Test
    public void testAddJourney() {
        int previousSize = estimatedTimetables.getAll().size();
        EstimatedVehicleJourney element = createEstimatedVehicleJourney("1234", "4321", 0, 30, ZonedDateTime.now().plusMinutes(1), true);

        estimatedTimetables.add("test", element);

        assertTrue(estimatedTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testGetUpdatesOnly() {
        int previousSize = estimatedTimetables.getAll().size();

        estimatedTimetables.add("test", createEstimatedVehicleJourney("1234-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add("test", createEstimatedVehicleJourney("2345-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add("test", createEstimatedVehicleJourney("3456-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        // Added 3
        String requestorId = UUID.randomUUID().toString();

        assertEquals(previousSize+3, estimatedTimetables.getAllUpdates(requestorId).size());

        estimatedTimetables.add("test", createEstimatedVehicleJourney("4567-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));

        //Added one
        assertEquals(1, estimatedTimetables.getAllUpdates(requestorId).size());


        //None added
        assertEquals("Returning partial updates when nothing has changed", 0, estimatedTimetables.getAllUpdates(requestorId).size());

        //Verify that all elements still exist
        assertEquals(previousSize+4, estimatedTimetables.getAll().size());
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        estimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure, true));
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", estimatedTimetables.getAll().size() == expectedSize);

        estimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure, true));
        assertTrue("Updating Journey added element.", estimatedTimetables.getAll().size() == expectedSize);

        ZonedDateTime departure_2 = ZonedDateTime.now().plusHours(1);
        estimatedTimetables.add("test", createEstimatedVehicleJourney("54321", "4321", 0, 30, departure_2, true));
        expectedSize++;
        assertTrue("Adding Journey did not add element.", estimatedTimetables.getAll().size() == expectedSize);

        estimatedTimetables.add("test2", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure_2, true));
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", estimatedTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", estimatedTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", estimatedTimetables.getAll("test").size() == expectedSize-1);

    }

    @Test
    public void testUpdatedJourneyWrongOrder() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        String lineRefValue = "12345-wrongOrder";
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney(lineRefValue, "4321", 0, 10, departure, true);
        estimatedVehicleJourney.setRecordedAtTime(ZonedDateTime.now().plusMinutes(1));

        estimatedTimetables.add("test", estimatedVehicleJourney);
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", estimatedTimetables.getAll().size() == expectedSize);

        EstimatedVehicleJourney estimatedVehicleJourney1 = createEstimatedVehicleJourney(lineRefValue, "4321", 1, 20, departure, true);
        estimatedVehicleJourney1.setRecordedAtTime(ZonedDateTime.now());
        estimatedTimetables.add("test", estimatedVehicleJourney1);

        assertTrue("Updating Journey added element.", estimatedTimetables.getAll().size() == expectedSize);

        List<EstimatedCall> estimatedCallsList = estimatedTimetables.getAll().get(0).getEstimatedCalls().getEstimatedCalls();
        int size = estimatedCallsList.size();
        assertEquals("Older request should have been ignored.", 10, size);
    }

    @Test
    public void testUpdatedJourneyNoRecordedAtTime() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        String lineRefValue = "NoRecordtimeLineRef";
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney(lineRefValue, "4321", 0, 10, departure, true);
        estimatedVehicleJourney.setRecordedAtTime(null);

        estimatedTimetables.add("test", estimatedVehicleJourney);
        assertEquals("Adding Journey did not add element.", previousSize+1, estimatedTimetables.getAll().size());

        EstimatedVehicleJourney estimatedVehicleJourney1 = createEstimatedVehicleJourney(lineRefValue, "4321", 1, 20, departure, true);
        estimatedVehicleJourney1.setRecordedAtTime(null);
        estimatedTimetables.add("test", estimatedVehicleJourney1);

        assertEquals("Updating Journey added element.", previousSize+1, estimatedTimetables.getAll().size());


        boolean checkedMatchingJourney = false;
        List<EstimatedVehicleJourney> all = estimatedTimetables.getAll();
        for (EstimatedVehicleJourney vehicleJourney : all) {
            if (lineRefValue.equals(vehicleJourney.getLineRef().getValue())) {
                List<EstimatedCall> estimatedCallsList = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
                int size = estimatedCallsList.size();
                assertEquals("Older request should have been ignored.", 10, size);
                checkedMatchingJourney = true;
            }
        }
        assertTrue("Did not check matching VehicleJourney", checkedMatchingJourney);
    }

    @Test
    public void testPartiallyUpdatedJourney() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        //Adding ET-data with stops 0-20
        String lineRefValue = "12345-partialUpdate";
        estimatedTimetables.add("test", createEstimatedVehicleJourney(lineRefValue, "4321", 0, 20, departure, false));
        assertEquals("Adding Journey did not add element.", previousSize+1, estimatedTimetables.getAll().size());

        //Adding ET-data with stops 10-30
        ZonedDateTime updatedDeparture = ZonedDateTime.now().plusHours(2);
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney(lineRefValue, "4321", 10, 30, updatedDeparture, false);

        List<EstimatedCall> callList = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
        EstimatedCall lastCall = callList.get(callList.size() - 1);
        assertEquals(updatedDeparture, lastCall.getExpectedArrivalTime());

        estimatedTimetables.add("test", estimatedVehicleJourney);
        assertEquals("Updating Journey should not add element.", previousSize+1, estimatedTimetables.getAll().size());


        boolean checkedMatchingJourney = false;
        List<EstimatedVehicleJourney> all = estimatedTimetables.getAll();
        for (EstimatedVehicleJourney vehicleJourney : all) {
            if (lineRefValue.equals(vehicleJourney.getLineRef().getValue())) {
                List<EstimatedCall> estimatedCallsList = vehicleJourney.getEstimatedCalls().getEstimatedCalls();

                int size = estimatedCallsList.size();
                assertEquals("List of EstimatedCalls have not been joined as expected.", 30, size);
                assertEquals("Original call has wrong timestamp", departure, estimatedCallsList.get(0).getExpectedArrivalTime());
                assertEquals("Updated call has wrong timestamp", updatedDeparture, estimatedCallsList.get(estimatedCallsList.size()-1).getExpectedArrivalTime());

                checkedMatchingJourney = true;
            }
        }

        assertTrue("Did not check matching VehicleJourney", checkedMatchingJourney);

    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime time, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(isComplete);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = startOrder; i < callCount; i++) {

            EstimatedCall call = new EstimatedCall();
                call.setAimedArrivalTime(time);
                call.setExpectedArrivalTime(time);
                call.setAimedDepartureTime(time);
                call.setExpectedDepartureTime(time);
                call.setOrder(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
