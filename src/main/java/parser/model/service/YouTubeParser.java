package parser.model.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
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
    private static final String APPLICATION_NAME = "YouTube Data Parser";
    private static final long MAX_RESULTS_PER_PAGE = 8;
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

    public Channel fetchChannelDetails(String channelId) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();
        YouTube.Channels.List request = youtubeService.channels().list("snippet,contentDetails,statistics");
        ChannelListResponse response = request.setId(channelId).setKey(apiKey).execute();
        return response.getItems().isEmpty() ? null : response.getItems().get(0);
    }

    public ChannelData getChannelData(String channelId) throws GeneralSecurityException, IOException {
        Channel channel = fetchChannelDetails(channelId);
        if (channel == null) {
            return null;
        }

        long videoCount = channel.getStatistics().getVideoCount().longValue();
        long averageViews = channel.getStatistics().getViewCount().longValue()/videoCount;

        return ChannelData.builder()
                .title(channel.getSnippet().getTitle())
                .url("https://www.youtube.com/channel/" + channelId)
                .subscribersCount(channel.getStatistics().getSubscriberCount().longValue())
                .totalViews(averageViews)
                .lastVideoDate(getLastVideoDate(channelId))
                .secondLastVideoDate(getSecondLastVideoDate(channelId))
                .totalVideos(videoCount)
                .language(getLanguage(channelId))
                .tags(getTags(channelId))
                .isChildFriendly(isChannelChildFriendly(channelId))
                .contactEmail(null)
                .build();
    }


    public List<String> searchForChannels(String query) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();
        YouTube.Search.List searchRequest = youtubeService.search()
                .list("snippet")
                .setQ(query)
                .setType("channel")
                .setFields("items(id/channelId)")
                .setMaxResults(MAX_RESULTS_PER_PAGE)
                .setKey(apiKey);

        SearchListResponse searchResponse = searchRequest.execute();
        List<String> channelIds = new ArrayList<>();

        for (SearchResult result : searchResponse.getItems()) {
            channelIds.add(result.getId().getChannelId());
        }

        return channelIds;
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
                    .setMaxResults(MAX_RESULTS_PER_PAGE)
                    .setKey(apiKey);

            if (nextPageToken != null) {
                searchRequest.setPageToken(nextPageToken);
            }

            SearchListResponse searchResponse = searchRequest.execute();
            List<String> foundChannelIds = new ArrayList<>();

            for (SearchResult result : searchResponse.getItems()) {
                foundChannelIds.add(result.getId().getChannelId());
            }

            List<Channel> channels = getChannelDetails(youtubeService, foundChannelIds);
            for (Channel channel : channels) {
                long subscriberCount = channel.getStatistics().getSubscriberCount().longValue();
                if (subscriberCount >= minSubscribers) {
                    channelIds.add(channel.getId());
                }
            }

            nextPageToken = searchResponse.getNextPageToken();
        } while (nextPageToken != null);

        return channelIds;
    }

    private List<Channel> getChannelDetails(YouTube youtubeService, List<String> channelIds) throws IOException {
        YouTube.Channels.List channelRequest = youtubeService.channels()
                .list("statistics")
                .setId(String.join(",", channelIds))
                .setFields("items(id,statistics/subscriberCount)")
                .setKey(apiKey);

        ChannelListResponse channelResponse = channelRequest.execute();
        return channelResponse.getItems();
    }

    private String getLastVideoDate(String channelId) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();

        YouTube.Search.List searchRequest = youtubeService.search().list("snippet");
        searchRequest.setChannelId(channelId);
        searchRequest.setOrder("date");
        searchRequest.setMaxResults(1L);
        searchRequest.setKey(apiKey);

        SearchListResponse searchResponse = searchRequest.execute();

        if (searchResponse.getItems().isEmpty()) {
            return "No videos found";
        }

        return searchResponse.getItems().get(0).getSnippet().getPublishedAt().toString();
    }

    private String getSecondLastVideoDate(String channelId) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();

        YouTube.Search.List searchRequest = youtubeService.search().list("snippet");
        searchRequest.setChannelId(channelId);
        searchRequest.setOrder("date");
        searchRequest.setMaxResults(2L);
        searchRequest.setKey(apiKey);

        SearchListResponse searchResponse = searchRequest.execute();

        if (searchResponse.getItems().size() < 2) {
            return "No second last video found";
        }

        return searchResponse.getItems().get(1).getSnippet().getPublishedAt().toString();
    }


    private String getLanguage(String channelId) throws GeneralSecurityException, IOException {
        Channel channel = fetchChannelDetails(channelId);
        if (channel != null && channel.getSnippet() != null) {
            return channel.getSnippet().getDefaultLanguage();
        }
        return "unknown";
    }

    private List<String> getTags(String channelId) throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        YouTube youtubeService = new YouTube.Builder(httpTransport, jsonFactory, null)
                .setApplicationName("your-application-name")
                .build();

        YouTube.Search.List searchRequest = youtubeService.search().list("snippet");
        searchRequest.setChannelId(channelId);
        searchRequest.setOrder("date");
        searchRequest.setMaxResults(1L);
        searchRequest.setKey(apiKey);

        SearchListResponse searchResponse = searchRequest.execute();

        if (searchResponse.getItems().isEmpty()) {
            return List.of("No tags available");
        }

        String videoId = searchResponse.getItems().get(0).getId().getVideoId();

        YouTube.Videos.List videoRequest = youtubeService.videos().list("snippet");
        videoRequest.setId(videoId);
        videoRequest.setKey(apiKey);

        VideoListResponse videoResponse = videoRequest.execute();

        if (videoResponse.getItems().isEmpty()) {
            return List.of("No tags available");
        }

        Video video = videoResponse.getItems().get(0);
        List<String> tags = video.getSnippet().getTags();

        return (tags != null && !tags.isEmpty()) ? tags : List.of("No tags available");
    }


    private boolean isChannelChildFriendly(String channelId) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();

        YouTube.Channels.List channelRequest = youtubeService.channels().list("status");
        channelRequest.setId(channelId);
        channelRequest.setKey(apiKey);

        ChannelListResponse channelResponse = channelRequest.execute();

        if (channelResponse.getItems().isEmpty()) {
            return false;
        }

        Channel channel = channelResponse.getItems().get(0);
        Boolean madeForKids = channel.getStatus().getMadeForKids();

        return madeForKids != null && madeForKids;
    }

}
