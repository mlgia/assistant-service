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

import com.ibm.watson.developer_cloud.assistant.v1.Assistant;
import com.ibm.watson.developer_cloud.assistant.v1.model.Context;
import com.ibm.watson.developer_cloud.assistant.v1.model.InputData;
import com.ibm.watson.developer_cloud.assistant.v1.model.MessageOptions;
import com.ibm.watson.developer_cloud.assistant.v1.model.MessageResponse;

import es.accenture.mlgia.dto.MessageDTO;
import es.accenture.mlgia.dto.ParkingType;
import es.accenture.mlgia.dto.PredictDTO;
import es.accenture.mlgia.dto.PredictResultDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class AssistantController {
	
	private Assistant service;
	
	private HashMap<String, Context> contexts;
	
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
	
	@PostConstruct
	public void setup() {
		service = new Assistant(version);
		service.setUsernameAndPassword(username, password);
		contexts = new HashMap<String, Context>();
	}
	
	@RequestMapping(value = "/assistant", method = RequestMethod.POST)
	public @ResponseBody MessageDTO sendMessage(@RequestBody MessageDTO message) {
		
		Context context = null;
		MessageResponse response = null;
		String messageOut = "";
		
		if (message.getMessageIn() == null) {
			message.setMessageIn("");
		}
		
		// Retrieve the context if the conversation has started
		if (message.getConversationId() != null && !"".equals(message.getConversationId())) {
			context = contexts.get(message.getConversationId());
		}
		
		// Prepare options for message in the specific context
		MessageOptions newMessageOptions = new MessageOptions.Builder()
				  .workspaceId(workspaceId)
				  .input(new InputData.Builder(message.getMessageIn()).build())
				  .context(context)
				  .build();
		
		// Send message in
		response = service.message(newMessageOptions).execute();

		// the context must be updated always
		contexts.put(response.getContext().getConversationId(), response.getContext());
		
		messageOut = response.getOutput().getText().get(0);
		
		if (response.getContext().get("parkingPlace") != null && !"".equals(response.getContext().get("parkingPlace")) &&
			response.getContext().get("parkingDate") != null && !"".equals(response.getContext().get("parkingDate")) && 
			response.getContext().get("parkingTime") != null && !"".equals(response.getContext().get("parkingTime"))) {
			
			message.setMessagePredictOut( predict(response.getContext().get("parkingPlace").toString(), response.getContext().get("parkingDate").toString(), response.getContext().get("parkingTime").toString()) );
		}
		
		log.info("User: {}", message.getMessageIn());
		log.info("Watson: {}", response.getOutput().getText().get(0));
		log.info("Predicted: {}", message.getMessagePredictOut());
		log.info("Parking: {}", response.getContext().get("parkingPlace") );
		log.info("Date: {}", response.getContext().get("parkingDate") );
		log.info("Time: {}", response.getContext().get("parkingTime") );
		
		message.setMessageOut(messageOut);
		message.setConversationId(response.getContext().getConversationId());
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
