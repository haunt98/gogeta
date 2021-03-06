package com.zalopay.gameplay.receptionist.buz;


import com.zalopay.gameplay.receptionist.cache.CacheClient;
import com.zalopay.gameplay.receptionist.config.QueueConfig;
import com.zalopay.gameplay.receptionist.constant.DetectTransStatusEnum;
import com.zalopay.gameplay.receptionist.model.DetectTransEntity;
import com.zalopay.gameplay.receptionist.model.LogDetectEntity;
import com.zalopay.gameplay.receptionist.utils.AppUtils;
import com.zalopay.gameplay.receptionist.utils.QueueUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;


@Component
@Scope(value = "prototype")
public class DetectBuz {

    @Autowired
    AppUtils appUtils;

    @Autowired
    CacheClient cacheClient;

    @Autowired
    QueueUtils queueUtils;

    @Autowired
    QueueConfig queueConfig;

    private static final Logger logger = Logger.getLogger(DetectBuz.class.getSimpleName());


    private DetectTransEntity trans;

    public DetectBuz(){
        this.trans = new DetectTransEntity();
    }
    public DetectBuz(DetectTransEntity detectTransEntity){
        this.trans = detectTransEntity;
    }

    public void setTrans(DetectTransEntity trans) {
        this.trans = trans;
    }

    public void processDetect(LogDetectEntity logDetectEntity){
        try {
            if(!getInfoFromRequestToTrans(logDetectEntity, trans)){
                trans.setTransStatus(DetectTransStatusEnum.UNAVAILABLE_VALUE_REQUEST_DETECT_OBJECT.getStatus());
                saveTransToCache(trans);
                return;
            }
            if(checkDuplicate(trans)){
                trans.setTransStatus(DetectTransStatusEnum.TRANS_IS_DETECTED.getStatus());
                return;
            }

            trans.setTransStatus(DetectTransStatusEnum.PROCESSING.getStatus());

            if(!saveTransToCache(trans)){
                trans.setTransStatus(DetectTransStatusEnum.SAVE_TRANS_TO_CACHE_FAIL.getStatus());
                return;
            }
            if(!processDetectObject(trans)){
                trans.setTransStatus(DetectTransStatusEnum.SEND_MESSAGE_DETECT_OBJECT_QUEUE_FAIL.getStatus());
                return;
            }
        }catch (Exception e){
            logger.warning("Fail at detect buz %" + trans.getRequestUrl());
            trans.setTransStatus(DetectTransStatusEnum.EXCEPTION.getStatus());
            this.saveTransToCache(trans);
        }
    }

    public boolean getInfoFromRequestToTrans(LogDetectEntity logDetectEntity, DetectTransEntity trans) {
        try{
            trans.setRequestUrl(logDetectEntity.getRequestDetectEntity().getUrl());
            trans.setDetectTransId(appUtils.generateUniqueID());
            return true;
        }catch (Exception e){
            return false;
        }
    }

    public boolean checkDuplicate(DetectTransEntity trans){
        if(cacheClient.getTransFromCache(trans.getDetectTransId()) != null){
            trans.setTransStatus(DetectTransStatusEnum.IS_DETECTED_THIS_OBJECT.getStatus());
            return true;
        }
        return false;
    }
    public boolean saveTransToCache(DetectTransEntity trans){
         if(cacheClient.saveToCache(trans)){
             trans.setSavedToCache(true);
             return true;
         }
         trans.setSavedToCache(false);
         return false;
    }
    public boolean processDetectObject(DetectTransEntity trans){
        String topicDetectObject = queueConfig.getDetectObjectKafkaQueue();
        if(!queueUtils.sendMessage(trans,topicDetectObject)){
            return false;
        }
        return true;
    }
}
