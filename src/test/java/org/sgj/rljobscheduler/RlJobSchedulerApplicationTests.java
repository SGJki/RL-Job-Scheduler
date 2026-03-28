package org.sgj.rljobscheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


//import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Rollback DB changes after test
class RlJobSchedulerApplicationTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        public void testAuthFlow() throws Exception {
            String username = "testuser_" + System.currentTimeMillis();
            String password = "password123";

            // 1. Register
            mockMvc.perform(post("/api/auth/register")
                            .param("username", username)
                            .param("password", password))
                    .andExpect(status().isOk());

            // 2. Login
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .param("username", username)
                            .param("password", password))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseContent = loginResult.getResponse().getContentAsString();
            Map<String, String> responseMap = objectMapper.readValue(responseContent, Map.class);
            String token = responseMap.get("token");

            assertNotNull(token, "Token should not be null");
            assertTrue(token.length() > 20, "Token should be valid JWT");

            // 3. Access Protected Endpoint without Token (Should Fail 403)
            mockMvc.perform(get("/tasks/fragment"))
                    .andExpect(status().isForbidden());

            // 4. Access Protected Endpoint with Token (Should Succeed 200)
            mockMvc.perform(get("/tasks/fragment")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // 5. Access Public Endpoint (Should Succeed 200)
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk());
        }
    }

