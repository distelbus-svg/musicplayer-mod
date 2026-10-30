package com.musicplayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class M3UHelper {

    public static boolean exportToM3U(Playlist playlist, File outputFile) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("#EXTM3U");
            for (SongInfo song : playlist.getSongs()) {
                lines.add("#EXTINF:-1," + song.getTitle());
                lines.add(song.getPath());
            }
            Files.write(outputFile.toPath(), lines);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Playlist importFromM3U(File m3uFile) {
        try {
            List<String> lines = Files.readAllLines(m3uFile.toPath());
            File parentDir = m3uFile.getParentFile();
            String folderName = m3uFile.getName();
            int dot = folderName.lastIndexOf('.');
            if (dot > 0) folderName = folderName.substring(0, dot);
            Playlist playlist = new Playlist(folderName);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String path = line;
                File songFile = new File(path);

                if (!songFile.isAbsolute()) {
                    songFile = new File(parentDir, path);
                }

                if (songFile.exists() && songFile.isFile()) {
                    String lower = songFile.getName().toLowerCase();
                    if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) {
                        String title = songFile.getName();
                        int d = title.lastIndexOf('.');
                        if (d > 0) title = title.substring(0, d);
                        String artist = "Local File";
                        if (i > 0 && lines.get(i - 1).startsWith("#EXTINF:")) {
                            String extinf = lines.get(i - 1);
                            int comma = extinf.lastIndexOf(',');
                            if (comma >= 0) {
                                String label = extinf.substring(comma + 1).trim();
                                if (label.contains(" - ")) {
                                    String[] parts = label.split(" - ", 2);
                                    artist = parts[0].trim();
                                    title = parts[1].trim();
                                }
                            }
                        }
                        playlist.addSong(new SongInfo(title, artist, songFile.getAbsolutePath(), SongInfo.SourceType.LOCAL));
                    }
                }
            }
            return playlist;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
