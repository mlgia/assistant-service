package es.accenture.mlgia.controller;

import java.util.Collections;

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
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
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
	
	//private HashMap<String, String> sessions;
	
	@Value("${service.assistant.workspace}")
	private String workspaceId;
	
	@Value("${service.assistant.username}")
	private String username;
	
	@Value("${service.assistant.password}")
	private String password;
	
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
		
		//service.setUsernameAndPassword(username, password);
		//contexts = new HashMap<String, Context>();
		//sessions = new HashMap<String, String>();
	}
	
	@RequestMapping(value = "/assistant", method = RequestMethod.POST)
	public @ResponseBody MessageDTO sendMessage(@RequestBody MessageDTO message) {
		
		// Context context = null;
		String sessionId = null;
		//MessageResponse response = null;
		String messageOut = "";
		
		if (message.getMessageIn() == null) {
			message.setMessageIn("");
		}
		
		// Retrieve the context if the conversation has started
		if (message.getConversationId() != null && !"".equals(message.getConversationId())) {
			//context = contexts.get(message.getConversationId());
			sessionId = message.getConversationId();
		} else {
			CreateSessionOptions sessionOptions = new CreateSessionOptions.Builder(assistantId).build();
			SessionResponse response = service.createSession(sessionOptions).execute().getResult();
			//System.out.println(response);
			sessionId = response.getSessionId();
			//sessions.put(message.getConversationId(), sessionId);
		}
				
		// Prepare options for message in the specific context
//		MessageOptions newMessageOptions = new MessageOptions.Builder()
//				  .workspaceId(workspaceId)
//				  .input(new InputData.Builder(message.getMessageIn()).build())
//				  .context(context)
//				  .build();
		
		// Send message in
		// response = service.message(newMessageOptions).execute();
		
		MessageInput input = new MessageInput.Builder()
				  .messageType(MessageInput.MessageType.TEXT)
				  .text(message.getMessageIn())
				  .build();

		MessageOptions options = new MessageOptions.Builder(assistantId, sessionId)
		  .input(input)
		  .build();

		MessageResponse response = service.message(options).execute().getResult();

		System.out.println(response);
		
		messageOut = response.getOutput().getGeneric().get(0).getText();

		// the context must be updated always
		//contexts.put(response.getContext().getConversationId(), response.getContext());
		
		//messageOut = response.getOutput().getText().get(0);
		
		
		/*
		if (response.getContext().get("parkingPlace") != null && !"".equals(.get("parkingPlace")) &&
			response.getContext().get("parkingDate") != null && !"".equals(response.getContext().get("parkingDate")) && 
			response.getContext().get("parkingTime") != null && !"".equals(response.getContext().get("parkingTime"))) {
			
			message.setMessagePredictOut( predict(response.getContext().get("parkingPlace").toString(), response.getContext().get("parkingDate").toString(), response.getContext().get("parkingTime").toString()) );
		}
		*/
		log.info("User: {}", message.getMessageIn());
		//log.info("Watson: {}", response.getOutput().getText().get(0));
		log.info("Predicted: {}", message.getMessagePredictOut());
		//log.info("Parking: {}", response.getContext().get("parkingPlace") );
		//log.info("Date: {}", response.getContext().get("parkingDate") );
		//log.info("Time: {}", response.getContext().get("parkingTime") );
		
		message.setMessageOut(messageOut);
		//message.setConversationId(response.getContext().getConversationId());
		message.setConversationId(sessionId);
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
