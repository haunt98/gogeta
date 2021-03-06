package com.zalopay.gameplay.receptionist.queue;

import com.zalopay.gameplay.receptionist.cache.CacheClient;
import com.zalopay.gameplay.receptionist.config.QueueConfig;
import com.zalopay.gameplay.receptionist.constant.DetectTransStatusEnum;
import com.zalopay.gameplay.receptionist.model.DetectTransEntity;
import com.zalopay.gameplay.receptionist.model.image.DetectImageRequest;
import com.zalopay.gameplay.receptionist.model.image.DetectImageResponse;
import com.zalopay.gameplay.receptionist.utils.ImageApiUtils;
import com.zalopay.gameplay.receptionist.utils.QueueUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DetectObjectQueueListener {

    @Autowired
    ImageApiUtils imageApiUtils;

    @Autowired
    CacheClient cacheClient;

    @Autowired
    QueueUtils queueUtils;

    @Autowired
    QueueConfig queueConfig;

    @KafkaListener(topics = "${kafka.topic.detectObjectKafkaQueue}")
    public void processDetectObject(DetectTransEntity trans){
        if(trans == null || trans.getRequestUrl().equals("")){
            return;
        }
        DetectImageResponse response = processDetectByCallImageService(trans);
        if(response == null){
            trans.setTransStatus(DetectTransStatusEnum.CALL_IMAGE_SERVICE_DETECT_FAIL.getStatus());
            cacheClient.saveToCache(trans);
            return;
        }
        transferInfoResponseToTrans(trans,response);
        if(response.getStatus() == DetectTransStatusEnum.PROCESSING.getStatus()){
            if(processGetStatusDetectObject(trans) == false){
                trans.setTransStatus(DetectTransStatusEnum.SEND_MESSAGE_GET_STATUS_DETECT_OBJECT_FAIL.getStatus());
                cacheClient.saveToCache(trans);
                return;
            }
        }
        if(response.getStatus() == DetectTransStatusEnum.SUCCESSFUL.getStatus()){
            trans.setTransStatus(DetectTransStatusEnum.SUCCESSFUL.getStatus());
            cacheClient.saveToCache(trans);
            return;
        }
    }

    private void transferInfoResponseToTrans(DetectTransEntity trans, DetectImageResponse response) {
        trans.setIdDetectImageResponse(response.getUuid());
        trans.setResponseUrl(response.getUrl());
    }

    private DetectImageResponse processDetectByCallImageService(DetectTransEntity trans){
        DetectImageRequest request = new DetectImageRequest(trans);
        return imageApiUtils.processDetectObject(request);
    }
    public boolean processGetStatusDetectObject(DetectTransEntity trans){
        String topicGetStatusDetectObject = queueConfig.getGetStatusDetectObjectQueue();
        if(!queueUtils.sendMessage(trans,topicGetStatusDetectObject)){
            return false;
        }
        return true;
    }

}
