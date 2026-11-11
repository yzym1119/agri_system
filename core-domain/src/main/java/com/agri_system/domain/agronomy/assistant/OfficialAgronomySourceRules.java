package com.agri_system.domain.agronomy.assistant;

import java.net.URI;
import java.util.Set;

/** 单一原页目的地规则，工作流与传输共用，不接受后缀或任意重定向。 */
public final class OfficialAgronomySourceRules {
    private static final Set<String> HOSTS=Set.of("moa.gov.cn","www.moa.gov.cn");
    private OfficialAgronomySourceRules() {}
    public static URI require(String value) {
        try {
            if(value==null||value.length()>2048||!value.chars().allMatch(c->c>32&&c<127)||value.contains("\\")||value.contains("%"))throw new IllegalArgumentException();
            var uri=URI.create(value);
            if(!"https".equals(uri.getScheme())||!HOSTS.contains(uri.getHost())||uri.getPort()!=-1&&uri.getPort()!=443
                    ||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                    ||uri.getPath()==null||!uri.getPath().startsWith("/")||uri.getPath().contains(".."))throw new IllegalArgumentException();
            return URI.create("https://"+uri.getHost()+uri.getRawPath());
        }catch(RuntimeException invalid){throw new AgronomySourceFailure(AgronomySourceFailure.Reason.INPUT);}
    }
    public static boolean allowed(String value){try{require(value);return true;}catch(AgronomySourceFailure invalid){return false;}}
}
