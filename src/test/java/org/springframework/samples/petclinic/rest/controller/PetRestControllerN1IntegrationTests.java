package org.springframework.samples.petclinic.rest.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class PetRestControllerN1IntegrationTests extends AbstractPetRestControllerN1IntegrationTests {
}
