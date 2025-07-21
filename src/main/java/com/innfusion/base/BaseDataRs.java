package com.innfusion.base;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class BaseDataRs implements Serializable {

    private String message;
    private Map<String, Object> data;

    public BaseDataRs(String message) {
        this.message = message;
    }

}
