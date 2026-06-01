package com.cyan.dto;

import lombok.Data;

@Data
public class SysPostDTO {
    private Long postId;
    private String postCode;
    private String postName;
    private Integer postSort;
    private String status;
    private String remark;
}