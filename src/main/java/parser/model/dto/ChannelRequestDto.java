package parser.model.dto;

import lombok.Data;

@Data
public class ChannelRequestDto {
    private String keyword;
    private Integer minSubscribers;
}
