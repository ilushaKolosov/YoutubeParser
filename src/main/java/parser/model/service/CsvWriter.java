package parser.model.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import parser.model.entity.ChannelData;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CsvWriter {

    public void writeChannelsToCsv(List<ChannelData> channels, String fileName) throws IOException {
        try (FileWriter csvWriter = new FileWriter(fileName)) {
            csvWriter.append("Название,Ссылка,Подписчики,Просмотры,Дата последнего видео,Дата предпоследнего видео,Всего видео,Язык,Теги,Детский контент,Почта\n");

            for (ChannelData channel : channels) {
                csvWriter.append(String.join(",",
                        channel.getTitle(),
                        channel.getUrl(),
                        String.valueOf(channel.getSubscribersCount()),
                        String.valueOf(channel.getTotalViews()),
                        channel.getLastVideoDate(),
                        channel.getSecondLastVideoDate(),
                        String.valueOf(channel.getTotalVideos()),
                        channel.getLanguage() != null ? channel.getLanguage() : "Не указан",
                        String.join(";", channel.getTags()),
                        channel.isChildFriendly() ? "Да" : "Нет",
                        channel.getContactEmail() != null ? channel.getContactEmail() : "Не указано"
                ));
                csvWriter.append("\n");
            }
        }
    }
}
