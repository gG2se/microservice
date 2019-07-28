package com.gg2se.microservice.configServer.config.service;

import com.gg2se.microservice.configServer.config.util.SpringContextUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "${spring.application.name}/service/config")
public class ConfigService {

    private static Logger log = LoggerFactory.getLogger(ConfigService.class);
    @Autowired
    private RestTemplate restTemplate;

    @RequestMapping(value = "getProperty", method = {RequestMethod.GET, RequestMethod.POST})
    public Map getProperty(HttpServletRequest request) {
        String keys = request.getParameter("keys");
        String profiles = request.getParameter("profiles");
        String defaultValues = request.getParameter("defaultValues");
        if (StringUtils.isBlank(profiles)) {
            profiles = "common";
        }
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();
        if (StringUtils.isBlank(keys)) {
            resultMap.put("code", 400);
            resultMap.put("message", "请传入配置项参数");
            return resultMap;
        }
        EnvironmentController environmentController = SpringContextUtils.getBean(EnvironmentController.class);
        String[] arrKey = keys.split(",");
        String[] arrProfile = profiles.split(",");
        String[] arrDefaultValue = defaultValues.split(",");

        int i = 0;
        //取各个key的值
        for (String key : arrKey) {
            String value = "";
            //取界面传过来的默认值
            if (arrDefaultValue.length > i) {
                value = arrDefaultValue[i];
            }
            //遍历所属系统
            for (String profilesKey : arrProfile) {
                Environment env = environmentController.labelled("application", profilesKey, null);
                List<PropertySource> ps = env.getPropertySources();
                for (PropertySource source : ps) {
                    Object obj = source.getSource().get(key);
                    if (obj != null) {
                        value = obj.toString();
                    }
                }
            }
            resultData.put(key, value);
            i++;
        }
        resultMap.put("code", 200);
        resultMap.put("data", resultData);
        return resultMap;
    }

    @RequestMapping(value = "/refresh", method = {RequestMethod.GET, RequestMethod.POST})
    public Map refresh(HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        String serviceNames = request.getParameter("services");
        if (StringUtils.isBlank(serviceNames)) {
            resultMap.put("code", 400);
            resultMap.put("message", "入参为空，刷新配置失败！");
            return resultMap;
        }
        String[] arrServiceName = serviceNames.split(",");
        List<String> errorServices = new ArrayList<>();
        for (String service : arrServiceName) {
            if (service.startsWith("eureka-server") || service.startsWith("config-server")) {
                continue;
            }
            String url = "http://" + service + "/config-client/refresh";
            log.info("正在更新配置：" + url);
            Map map = restTemplate.getForEntity(url, Map.class).getBody();
            if (MapUtils.isEmpty(map) || (int) map.get("code") != 200) {
                errorServices.add(service);
            }
        }
        if (CollectionUtils.isNotEmpty(errorServices)) {
            resultMap.put("code", 500);
            resultMap.put("message", "服务[" + StringUtils.join(errorServices, ",") + "]，刷新配置失败！");
        } else {
            resultMap.put("code", 200);
            resultMap.put("message", "配置刷新成功！");
        }
        return resultMap;
    }

}
