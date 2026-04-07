package org.springframework.samples.petclinic.rest.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"jpa", "hsqldb"})
class OwnerRestControllerJpaN1IntegrationTests extends AbstractOwnerRestControllerN1IntegrationTests {
}
