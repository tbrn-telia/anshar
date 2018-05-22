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

package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.EstimatedExpectedDepartureTimeValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class EstimatedExpectedDepartureTimeValidatorTest extends CustomValidatorTest {

    private static EstimatedExpectedDepartureTimeValidator validator;
    private final String fieldName = "ExpectedDepartureTime";
    private final String comparisonField = "ExpectedArrivalTime";

    @BeforeClass
    public static void init() {
        validator = new EstimatedExpectedDepartureTimeValidator();
    }

    @Test
    public void testAimedArrivalOnly() throws Exception{
        String xml = createXml(fieldName, "2018-04-16T10:00:00+02:00");

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }


    @Test
    public void testAimedArrivalAndAimedDepartureEqual() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:00:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:00:00+02:00");

        String xml = "<PLACEHOLDER>" + arrival + departure + "</PLACEHOLDER>";

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testAimedArrivalBeforeAimedDeparture() throws Exception{
        String arrival = createXml(comparisonField, "2018-04-16T10:00:00+02:00");
        String departure = createXml(fieldName, "2018-04-16T10:02:00+02:00");

        String xml = "<PLACEHOLDER>" + arrival + departure + "</PLACEHOLDER>";

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testAimedArrivalAfterAimedDeparture() throws Exception{
        String arrival = createXml(comparisonField, "2018-04-16T10:02:00+02:00");
        String departure = createXml(fieldName, "2018-04-16T10:00:00+02:00");

        String xml = "<dummy>" + arrival + departure + "</dummy>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml).getFirstChild());
        assertNotNull("Invalid "+fieldName+" flagged as valid", valid);
    }
}
