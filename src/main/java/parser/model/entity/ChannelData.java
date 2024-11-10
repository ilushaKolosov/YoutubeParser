package parser.model.entity;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Component
public class ChannelData {
    private String title;
    private String url;
    private long subscribersCount;
    private long totalViews;
    private String lastVideoDate;
    private String secondLastVideoDate;
    private long totalVideos;
    private String language;
    private List<String> tags;
    private boolean isChildFriendly;
    private String contactEmail;
}
