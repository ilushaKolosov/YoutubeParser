package parser.model.dto;

import lombok.Data;

@Data
public class ChannelRequestWithSubscribersDto {
    private String keyword;
    private int minSubscribers;
}
