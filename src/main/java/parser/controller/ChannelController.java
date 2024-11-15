package parser.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import parser.model.dto.ChannelRequestDto;
import parser.model.entity.ChannelData;
import parser.model.service.CsvWriter;
import parser.model.service.YouTubeParser;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {
    private final CsvWriter csvWriter;
    private final YouTubeParser youTubeParser;

    @PostMapping("/with-subscribers")
    public ResponseEntity<String> getChannelsWithSubscribers(@RequestBody ChannelRequestDto request) {
        return processChannels(request.getKeyword(), request.getMinSubscribers());
    }

    @PostMapping("/without-subscribers")
    public ResponseEntity<String> getChannelsWithoutSubscribers(@RequestBody ChannelRequestDto request) {
        return processChannels(request.getKeyword(), null);
    }

    private ResponseEntity<String> processChannels(String keyword, Integer minSubscribers) {
        try {
            List<String> channelIds;
            List<ChannelData> channels = new ArrayList<>();

            if (minSubscribers != null) {
                channelIds = youTubeParser.searchForChannels(keyword, minSubscribers);
            } else {
                channelIds = youTubeParser.searchForChannels(keyword);
            }

            log.info("Found {} channels", channelIds.size());
            for (int i = 0; i < YouTubeParser.MAX_RESULTS_PER_PAGE; i++) {
                channels.add(youTubeParser.getChannelData(channelIds.get(i)));
                log.info("Channel {} saved", channelIds.get(i));
            }

            csvWriter.writeChannelsToCsv(channels, "youtube_channels.csv");
            log.info("File saved as youtube_channels.csv");

            return ResponseEntity.ok("Channels saved to youtube_channels.csv");

        } catch (Exception e) {
            log.error("Error processing channels", e);
            return ResponseEntity.status(500).body("Failed to process channels");
        }
    }
}
