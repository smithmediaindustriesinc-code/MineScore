package com.minescore.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Decodes MP3 with JLayer (LGPL 2.1, shipped as its own jar). Handles ID3 tags and VBR/LAME header
 * frames; a damaged frame partway through ends the song there rather than failing it.
 */
public final class Mp3Decoder {
    /** Decoding less than this (in frames of audio) means the file wasn't really an MP3. */
    private static final int MIN_FRAMES = 4410;

    private Mp3Decoder() {}

    public static Track decode(byte[] file) throws IOException {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(file));
        Decoder decoder = new Decoder();
        FlacDecoder.ShortList samples = new FlacDecoder.ShortList();
        int rate = 0;
        int channels = 0;
        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                try {
                    SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    rate = out.getSampleFrequency();
                    channels = out.getChannelCount();
                    short[] buffer = out.getBuffer();
                    for (int i = 0; i < out.getBufferLength(); i++) {
                        samples.add(buffer[i]);
                    }
                } catch (DecoderException | ArrayIndexOutOfBoundsException e) {
                    if (samples.size() / Math.max(channels, 1) >= MIN_FRAMES) {
                        break; // keep what decoded before the damage
                    }
                    throw new IOException("Not a valid MP3 file", e);
                } finally {
                    bitstream.closeFrame();
                }
            }
        } catch (BitstreamException e) {
            if (samples.size() / Math.max(channels, 1) < MIN_FRAMES) {
                throw new IOException("Not a valid MP3 file", e);
            }
        } finally {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
                // nothing to release for an in-memory stream
            }
        }
        if (channels == 0 || samples.size() / channels < MIN_FRAMES) {
            throw new IOException("No MP3 audio found");
        }
        return Track.convert(samples.toArray(), channels, rate);
    }
}
