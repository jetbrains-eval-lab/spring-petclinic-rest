package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.mapper.UserMapper;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.service.UserService;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class UserRestControllerTests {

    @Mock
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRestController userRestController;

    private MockMvc mockMvc;

    @BeforeEach
    void initVets() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(userRestController)
            .setControllerAdvice(new ExceptionControllerAdvice()).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserSuccess() throws Exception {
        User user = new User();
        user.setUsername("username");
        // Valid password with digits, letters and special characters
        user.setPassword("Pass1!word");
        user.setEnabled(true);
        user.addRole("OWNER_ADMIN");
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserWithPasswordTooShort() throws Exception {
        User user = new User();
        user.setUsername("username");
        // Password too short
        user.setPassword("Pw1!");
        user.setEnabled(true);
        user.addRole("OWNER_ADMIN");
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is(not(HttpStatus.CREATED.value())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserWithPasswordNoDigit() throws Exception {
        User user = new User();
        user.setUsername("username");
        // Password without digit
        user.setPassword("Password!");
        user.setEnabled(true);
        user.addRole("OWNER_ADMIN");
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is(not(HttpStatus.CREATED.value())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserWithPasswordNoLetter() throws Exception {
        User user = new User();
        user.setUsername("username");
        // Password without letter
        user.setPassword("12345!@#");
        user.setEnabled(true);
        user.addRole("OWNER_ADMIN");
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is(not(HttpStatus.CREATED.value())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserWithPasswordNoSpecialChar() throws Exception {
        User user = new User();
        user.setUsername("username");
        // Password without special character
        user.setPassword("Password123");
        user.setEnabled(true);
        user.addRole("OWNER_ADMIN");
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is(not(HttpStatus.CREATED.value())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserError() throws Exception {
        User user = new User();
        user.setUsername(""); // set empty username to force 400 error
        user.setPassword("Password1!");
        user.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest());
    }
}
