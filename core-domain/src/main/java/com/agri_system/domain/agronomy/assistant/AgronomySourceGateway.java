package com.agri_system.domain.agronomy.assistant;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** 只读官方原页。不是搜索摘要、付费Fetch或老板审核结论。 */
public interface AgronomySourceGateway {
    record Page(String url,String title,String articleText,LocalDate publishedOn,Instant fetchedAt,
            String documentSha256,String textSha256) {
        @Override public String toString(){return "AgronomyOfficialPage[正文隐藏]";}
    }
    Page fetch(String url,Duration maximum);
    static AgronomySourceGateway disabled(){return (url,maximum)->{throw new AgronomySourceFailure(AgronomySourceFailure.Reason.DISABLED);};}
}
