package com.transitpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")
class AuthIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
 private String registerAndGetToken(String email) throws Exception {
   String body = "{\"fullName\":\"Transit Tester\",\"email\":\""+email+"\",\"password\":\"TransitPulse!2026\"}";
   String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
     .andExpect(status().isCreated()).andExpect(jsonPath("$.data.token").exists()).andReturn().getResponse().getContentAsString();
   return mapper.readTree(response).path("data").path("token").asText();
 }
 @Test void registrationLoginAndProtectedEndpoint() throws Exception {
   String email = "test-"+System.nanoTime()+"@example.com";
   String token = registerAndGetToken(email);
   mvc.perform(get("/api/auth/me").header("Authorization", "Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
   mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"TransitPulse!2026\"}"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").exists());
   mvc.perform(get("/api/routes").header("Authorization", "Bearer "+token)).andExpect(status().isOk());
 }
 @Test void protectedEndpointRejectsAnonymousAndHealthRemainsPublic() throws Exception {
   mvc.perform(get("/api/routes")).andExpect(status().isUnauthorized());
   mvc.perform(get("/api/health")).andExpect(status().isOk());
 }
 @Test void invalidCredentialsAreRejected() throws Exception {
   mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
     .andExpect(status().isUnauthorized());
 }
}
