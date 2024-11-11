package parser.model.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequest;
import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import parser.model.entity.ChannelData;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YouTubeParser {
    public static final long MAX_RESULTS_PER_PAGE = 8;
    private static final String APPLICATION_NAME = "YouTube Data Parser";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Value("${youtube.api.key}")
    private String apiKey;

    public YouTube getService() throws GeneralSecurityException, IOException {
        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                request -> {}
        ).setApplicationName(APPLICATION_NAME).build();
    }

    private <T> T executeRequest(YouTubeRequest<T> request) throws IOException {
        return request.setKey(apiKey).execute();
    }

    public Channel fetchChannelDetails(String channelId) throws GeneralSecurityException, IOException {
        YouTube.Channels.List request = getService().channels().list("snippet,contentDetails,statistics");
        ChannelListResponse response = executeRequest(request.setId(channelId));
        return response.getItems().isEmpty() ? null : response.getItems().get(0);
    }

    public ChannelData getChannelData(String channelId) throws GeneralSecurityException, IOException {
        Channel channel = fetchChannelDetails(channelId);
        if (channel == null) return null;

        long videoCount = channel.getStatistics().getVideoCount().longValue();
        long averageViews = channel.getStatistics().getViewCount().longValue() / videoCount;

        return ChannelData.builder()
                .title(channel.getSnippet().getTitle())
                .url("https://www.youtube.com/channel/" + channelId)
                .subscribersCount(channel.getStatistics().getSubscriberCount().longValue())
                .totalViews(averageViews)
                .lastVideoDate(fetchVideoDate(channelId, 1))
                .secondLastVideoDate(fetchVideoDate(channelId, 2))
                .totalVideos(videoCount)
                .language(fetchChannelLanguage(channelId))
                .tags(fetchTags(channelId))
                .isChildFriendly(isChannelChildFriendly(channelId))
                .contactEmail(null)
                .build();
    }

    public List<String> searchForChannels(String query) throws GeneralSecurityException, IOException {
        return searchForChannels(query, 0);
    }

    public List<String> searchForChannels(String query, long minSubscribers) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();
        List<String> channelIds = new ArrayList<>();
        String nextPageToken = null;

        do {
            YouTube.Search.List searchRequest = youtubeService.search()
                    .list("snippet")
                    .setQ(query)
                    .setType("channel")
                    .setFields("nextPageToken,items(id/channelId)")
                    .setMaxResults(MAX_RESULTS_PER_PAGE);
            if (nextPageToken != null) searchRequest.setPageToken(nextPageToken);

            SearchListResponse searchResponse = executeRequest(searchRequest);
            List<String> foundChannelIds = new ArrayList<>();

            for (SearchResult result : searchResponse.getItems()) {
                foundChannelIds.add(result.getId().getChannelId());
            }

            for (Channel channel : fetchChannelDetailsList(youtubeService, foundChannelIds)) {
                if (channel.getStatistics().getSubscriberCount().longValue() >= minSubscribers) {
                    channelIds.add(channel.getId());
                }
            }

            nextPageToken = searchResponse.getNextPageToken();
        } while (nextPageToken != null);

        return channelIds;
    }

    private List<Channel> fetchChannelDetailsList(YouTube youtubeService, List<String> channelIds) throws IOException {
        YouTube.Channels.List channelRequest = youtubeService.channels()
                .list("statistics")
                .setId(String.join(",", channelIds))
                .setFields("items(id,statistics/subscriberCount)");
        return executeRequest(channelRequest).getItems();
    }

    private String fetchVideoDate(String channelId, long videoPosition) throws GeneralSecurityException, IOException {
        YouTube.Search.List searchRequest = getService().search().list("snippet")
                .setChannelId(channelId)
                .setOrder("date")
                .setMaxResults(videoPosition);
        SearchListResponse response = executeRequest(searchRequest);
        if (response.getItems().size() < videoPosition) return "No video found";
        return response.getItems().get((int) (videoPosition - 1)).getSnippet().getPublishedAt().toString();
    }

    private String fetchChannelLanguage(String channelId) throws GeneralSecurityException, IOException {
        Channel channel = fetchChannelDetails(channelId);
        return (channel != null && channel.getSnippet() != null) ? channel.getSnippet().getDefaultLanguage() : "unknown";
    }

    private List<String> fetchTags(String channelId) throws GeneralSecurityException, IOException {
        String videoId = fetchVideoId(channelId);
        if (videoId == null) return List.of("No tags available");

        YouTube.Videos.List videoRequest = getService().videos().list("snippet").setId(videoId);
        VideoListResponse videoResponse = executeRequest(videoRequest);

        Video video = videoResponse.getItems().isEmpty() ? null : videoResponse.getItems().get(0);
        List<String> tags = (video != null && video.getSnippet().getTags() != null) ? video.getSnippet().getTags() : List.of("No tags available");
        return tags;
    }

    private String fetchVideoId(String channelId) throws GeneralSecurityException, IOException {
        YouTube.Search.List searchRequest = getService().search().list("snippet")
                .setChannelId(channelId)
                .setOrder("date")
                .setMaxResults(1L);
        SearchListResponse response = executeRequest(searchRequest);
        return response.getItems().isEmpty() ? null : response.getItems().get(0).getId().getVideoId();
    }

    private boolean isChannelChildFriendly(String channelId) throws GeneralSecurityException, IOException {
        YouTube.Channels.List channelRequest = getService().channels().list("status").setId(channelId);
        ChannelListResponse channelResponse = executeRequest(channelRequest);

        Channel channel = channelResponse.getItems().isEmpty() ? null : channelResponse.getItems().get(0);
        return channel != null && Boolean.TRUE.equals(channel.getStatus().getMadeForKids());
    }
}
