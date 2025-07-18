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
import org.springframework.samples.petclinic.repository.UserRepository;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.service.UserService;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class UserRestControllerTests {

    @Mock
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRestController userRestController;

    @Autowired
    private LocalValidatorFactoryBean validator;

    private MockMvc mockMvc;

    @BeforeEach
    void initVets() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(userRestController)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .setValidator(validator)
            .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserSuccess() throws Exception {
        // Set up the userRepository mock to return null (no user found)
        when(userRepository.findEnabledUserByUsername(anyString())).thenReturn(null);

        User user = new User();
        user.setUsername("username");
        user.setPassword("password");
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
    void testCreateUserError() throws Exception {
        User user = new User();
        user.setUsername(""); // set empty username to force 400 error
        user.setPassword("password");
        user.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        String newVetAsJSON = mapper.writeValueAsString(userMapper.toUserDto(user));
        this.mockMvc.perform(post("/api/users")
            .content(newVetAsJSON).accept(MediaType.APPLICATION_JSON_VALUE).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateUserWithDuplicateEnabledUsername() throws Exception {
        // Set up an existing enabled user with the same username
        User existingUser = new User();
        existingUser.setUsername("duplicateUsername");
        existingUser.setPassword("password123");
        existingUser.setEnabled(true);

        // Mock the repository to return the existing user when checked
        when(userRepository.findEnabledUserByUsername("duplicateUsername")).thenReturn(existingUser);

        // Create a new user with the same username
        User newUser = new User();
        newUser.setUsername("duplicateUsername");
        newUser.setPassword("newPassword");
        newUser.setEnabled(true);
        newUser.addRole("OWNER_ADMIN");

        ObjectMapper mapper = new ObjectMapper();
        String newUserAsJSON = mapper.writeValueAsString(userMapper.toUserDto(newUser));

        // Perform the request and expect a validation error
        this.mockMvc.perform(post("/api/users")
            .content(newUserAsJSON)
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is(not(HttpStatus.CREATED)));
    }
}
