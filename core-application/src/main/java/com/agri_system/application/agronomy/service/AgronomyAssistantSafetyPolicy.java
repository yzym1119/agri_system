package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 首批开放观察记录类资料整理；其余由真实人工接力，不让模型自报安全。 */
final class AgronomyAssistantSafetyPolicy {
    record Decision(String kind,String reason,String topic,List<String> missingFacts) {}
    private static final Pattern PRIVATE=Pattern.compile("(?<![0-9])(?:1[3-9][0-9]{9}|[1-9][0-9]{16}[0-9xX])(?![0-9])|身份证|银行卡|密钥|密码|微信号|家庭住址|收货地址|手机号|电话号码");
    private static final Pattern PROFESSIONAL=Pattern.compile("病|虫|药|肥|混配|制剂|用量|配比|剂量|中毒|枯|黄叶|叶黄|烂根|斑点|死苗|萎|治疗|诊断|喷什么|打什么|补什么|加多少|施用|灌根|拌种|除草|杀菌|浓度|兑水|稀释|每亩|毫升|mg|ml|ppm|%|％",Pattern.CASE_INSENSITIVE);
    private static final Pattern INJECTION=Pattern.compile("忽略.{0,12}(指令|规则|上文)|系统提示|system.?prompt|developer.?message|api.?key|忽略安全|执行指令|调用工具|泄露|越狱|ignore.{0,20}(instruction|previous)|<script|javascript:",Pattern.CASE_INSENSITIVE);
    Decision assess(String question,String supplement,Facts facts) {
        String text=normalize(question)+normalize(supplement);
        if(privateText(text)||unsafeCharacters(text))return new Decision("CLARIFY","REMOVE_PRIVATE_DETAILS","",List.of());
        if(professional(text)||injection(text))return human("PROFESSIONAL_REVIEW");
        var missing=new ArrayList<String>();
        if(facts==null||blank(facts.region()))missing.add("region");
        if(facts==null||blank(facts.crop()))missing.add("crop");
        if(facts==null||blank(facts.growthStage()))missing.add("growthStage");
        if(!missing.isEmpty())return new Decision("CLARIFY","MISSING_FACTS","",List.copyOf(missing));
        for(String value:List.of(facts.region(),facts.crop(),facts.growthStage()))
            if(value.codePointCount(0,value.length())>24||!value.matches("[\\p{IsHan}A-Za-z0-9·（）() /-]+")
                    ||privateText(value)||injection(value)||unsafeCharacters(value)||value.matches(".*(?:村|镇|街|路|室|号).*"))
                return new Decision("CLARIFY","SIMPLIFY_FACTS","",List.of());
        String topic=topic(text);
        return topic==null?human("NEEDS_HUMAN_CONTEXT"):new Decision("REFERENCE","OBSERVATION_ONLY",topic,List.of());
    }
    static Decision human(String reason) {return new Decision("HUMAN",reason,"",List.of());}
    private static boolean blank(String text) {return text==null||text.isBlank();}
    static String normalize(String value) {return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    static boolean privateText(String text) {return PRIVATE.matcher(normalize(text)).find();}
    static boolean professional(String text) {return PROFESSIONAL.matcher(normalize(text)).find();}
    static boolean injection(String text) {return INJECTION.matcher(normalize(text)).find();}
    static boolean unsafeCharacters(String value) {return value.codePoints().anyMatch(c->Character.getType(c)==Character.FORMAT||Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t');}
    static boolean matchesTopic(String value,String topic) {
        String text=normalize(value);
        return switch(topic) {
            case "积水观察记录" -> text.contains("积水")||text.contains("排水");
            case "温湿度观察记录" -> text.contains("温度")||text.contains("湿度")||text.contains("温湿度");
            case "墒情观察记录" -> text.contains("墒情")||text.contains("土壤水分");
            case "苗情观察记录" -> text.contains("苗情")||text.contains("出苗")||text.contains("缺苗")||text.contains("长势");
            case "种子标签留存记录" -> text.contains("标签")||text.contains("包装")||text.contains("购买凭证");
            default -> false;
        };
    }
    private static String topic(String text) {
        if(!text.matches(".*(?:记录|观察|检查|查看|监测|测量|保存|留存|整理).*"))return null;
        if(text.contains("积水")||text.contains("排水"))return "积水观察记录";
        if(text.contains("温度")||text.contains("湿度")||text.contains("温湿度"))return "温湿度观察记录";
        if(text.contains("墒情")||text.contains("土壤水分"))return "墒情观察记录";
        if(text.contains("苗情")||text.contains("出苗")||text.contains("缺苗")||text.contains("长势"))return "苗情观察记录";
        if(text.contains("标签")||text.contains("购买凭证")||text.contains("包装"))return "种子标签留存记录";
        return null;
    }
}
