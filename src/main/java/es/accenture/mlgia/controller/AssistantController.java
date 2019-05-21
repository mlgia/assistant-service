package es.accenture.mlgia.controller;

import java.util.Collections;
import java.util.HashMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.ibm.cloud.sdk.core.service.security.IamOptions;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.MessageContext;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
import com.ibm.watson.assistant.v2.model.RuntimeEntity;
import com.ibm.watson.assistant.v2.model.SessionResponse;

import es.accenture.mlgia.dto.MessageDTO;
import es.accenture.mlgia.dto.ParkingType;
import es.accenture.mlgia.dto.PredictDTO;
import es.accenture.mlgia.dto.PredictResultDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class AssistantController {
	
	private Assistant service;
	
	private HashMap<String, MessageContext> contexts;
	
	@Value("${service.assistant.version}")
	private String version;
	
	@Value("${url.prediction}")
	private String urlPrediction;
	
	@Value("${message.prediction.yes}")
	private String msgPredictionYes;
	
	@Value("${message.prediction.no}")
	private String msgPredictionNo;
	
	@Value("${message.prediction.error}")
	private String msgPredictionError;
	
	@Value("${watson.apikey}")
	private String apikey;
	
	@Value("${watson.endpoint}")
	private String endpoint;
	
	@Value("${watson.assistantid}")
	private String assistantId;
	
	@PostConstruct
	public void setup() {
		IamOptions options = new IamOptions.Builder()
			    .apiKey(apikey)
			    .build();
		
		service = new Assistant(version, options);
		service.setEndPoint(endpoint);
		contexts = new HashMap<String, MessageContext>();
	}
	
	@RequestMapping(value = "/assistant", method = RequestMethod.POST)
	public @ResponseBody MessageDTO sendMessage(@RequestBody MessageDTO message) {
		
		MessageContext context;
		String sessionId = null;
		String messageOut = "";
		String parkingPlace = "";
		String parkingDate = "";
		String parkingTime = "";
		
		if (message.getMessageIn() == null) {
			message.setMessageIn("");
		}
		
		// Retrieve the context if the conversation has started
		if (message.getConversationId() != null && !"".equals(message.getConversationId())) {
			sessionId = message.getConversationId();
            context = contexts.get(message.getConversationId());
		} else {
			CreateSessionOptions sessionOptions = new CreateSessionOptions.Builder(assistantId).build();
			SessionResponse response = service.createSession(sessionOptions).execute().getResult();
			sessionId = response.getSessionId();
			context = new MessageContext();
		}
		
		context = new MessageContext();
		
		MessageInput input = new MessageInput.Builder()
				  .messageType(MessageInput.MessageType.TEXT)
				  .text(message.getMessageIn())
				  .build();

		MessageOptions options = new MessageOptions.Builder(assistantId, sessionId)
		  .input(input)
		  .context(context)
		  .build();

		MessageResponse response = service.message(options).execute().getResult();
		messageOut = response.getOutput().getGeneric().get(0).getText();
		
		log.info("User: {}", message.getMessageIn());
		log.info("Watson: {}", messageOut);
		log.info("Predicted: {}", message.getMessagePredictOut());
		
		for (RuntimeEntity entity : response.getOutput().getEntities()) {
			log.info( "{}: {}", entity.getEntity(), entity.getValue() );
			if (entity.getEntity().contentEquals("Aparcamiento")) {
				parkingPlace = entity.getValue();
			} else if (entity.getEntity().equals("sys-date")) {
				parkingDate = entity.getValue();
			} else if (entity.getEntity().equals("sys-time")) {
				parkingTime = entity.getValue();
			}
		}
		
		if (!parkingPlace.isEmpty() && !parkingDate.isEmpty() && !parkingTime.isEmpty()) {
			message.setMessagePredictOut( predict(parkingPlace, parkingDate, parkingTime) );
		}
		
		message.setMessageOut(messageOut);
		message.setConversationId(sessionId);
		
        contexts.put(sessionId, response.getContext());
		
		return message;
	}
	
	private String predict(String parking, String date, String time) {

		RestTemplate restTemplate = new RestTemplate();
		String message = "";
		PredictResultDTO out = null;
		
		Integer parkingId = ParkingType.getIdByName(parking);
		PredictDTO in = PredictDTO.builder().parkingId(parkingId).date(date).time(time).build();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		HttpEntity<Object> request = new HttpEntity<>(in, headers);

		try{
			out = restTemplate.postForObject(urlPrediction, request, PredictResultDTO.class);
		}
		catch(Exception e) {
			log.error(e.getMessage());
		}

		if (out != null && out.getPrediction() != null) {
			if (out.getPrediction().equals(1)) {
				message = msgPredictionYes;
			} else {
				message = msgPredictionNo;
			}
		} else {
			message = msgPredictionError;
		}
		
		return message;
	}
		
}
