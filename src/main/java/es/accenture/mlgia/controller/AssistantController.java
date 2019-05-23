package es.accenture.mlgia.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
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

import com.google.gson.internal.LinkedTreeMap;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import com.ibm.cloud.sdk.core.service.security.IamOptions;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.DialogNodeOutputOptionsElement;
import com.ibm.watson.assistant.v2.model.MessageContext;
import com.ibm.watson.assistant.v2.model.MessageContextSkills;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageInputOptions;
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
            if (context == null) {
            	context = new MessageContext();
            }
		} else {
			CreateSessionOptions sessionOptions = new CreateSessionOptions.Builder(assistantId).build();
			SessionResponse response = service.createSession(sessionOptions).execute().getResult();
			sessionId = response.getSessionId();
			context = new MessageContext();
		}
		
		MessageInputOptions inputOptions = new MessageInputOptions();
		inputOptions.setReturnContext(true);
		
		MessageInput input = new MessageInput.Builder()
				  .messageType(MessageInput.MessageType.TEXT)
				  .text(message.getMessageIn())
				  .options(inputOptions)
				  .build();

		MessageOptions options = new MessageOptions.Builder(assistantId, sessionId)
		  .input(input)
		  .context(context)
		  .build();

		MessageResponse response = null;
		try {
			response = service.message(options).execute().getResult();
		} catch (NotFoundException e) {
			log.info(e.getLocalizedMessage());
		}
		messageOut = response.getOutput().getGeneric().get(0).getText();
		
		if (response.getOutput().getGeneric().size() > 1 && response.getOutput().getGeneric().get(1).getResponseType().equals("option")) {
			List<String> optionsApar = new ArrayList<String>();
			for (DialogNodeOutputOptionsElement option : response.getOutput().getGeneric().get(1).getOptions()) {
				optionsApar.add(option.getLabel());
			} 
			message.setOptions(optionsApar);
		}
		
		log.info("User: {}", message.getMessageIn());
		log.info("Watson: {}", messageOut);
		
		
//		for (RuntimeEntity entity : response.getOutput().getEntities()) {
//			log.info( "{}: {}", entity.getEntity(), entity.getValue() );
//			if (entity.getEntity().contentEquals("Aparcamiento")) {
//				parkingPlace = entity.getValue();
//			} else if (entity.getEntity().equals("sys-date")) {
//				parkingDate = entity.getValue();
//			} else if (entity.getEntity().equals("sys-time")) {
//				parkingTime = entity.getValue();
//			}
//		}
		MessageContextSkills skill;
		//Map<String,Object> mainSkill;
		LinkedTreeMap<String, LinkedTreeMap> mainSkill = null;
		if (response != null && response.getContext() != null && response.getContext().getSkills() != null) {
			 skill = response.getContext().getSkills();
			 mainSkill = (LinkedTreeMap<String, LinkedTreeMap>) skill.getOrDefault("main skill", new LinkedTreeMap<>());	
			 LinkedTreeMap userDefined = mainSkill.getOrDefault("user_defined", new LinkedTreeMap<>());
			 parkingDate = (String) userDefined.getOrDefault("parkingDate", StringUtils.EMPTY);
			 parkingPlace = (String) userDefined.getOrDefault("parkingPlace", StringUtils.EMPTY);
			 parkingTime = (String) userDefined.getOrDefault("parkingTime", StringUtils.EMPTY);
			 
		}
		
		if (!parkingPlace.isEmpty() && !parkingDate.isEmpty() && !parkingTime.isEmpty()) {
			message.setMessagePredictOut( predict(parkingPlace, parkingDate, parkingTime) );
			log.info("Predicted: {}", message.getMessagePredictOut());
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
