package vd_server.helper;

import vd_server.helper.objects.FileUpload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by pouya on 1/19/17.
 */
public class Writer implements CompletionHandler<Integer, AsynchronousFileChannel> {

    // need to keep track of the next position.
    final int BUFFER_SIZE = 512000;
    int pos = 0;
    AsynchronousFileChannel channel = null;
    AsynchronousSocketChannel socketchannel = null;
    ByteBuffer buffer = null;
    int total = 1;
    CompletionHandler<Integer, AsynchronousFileChannel> current;

    public void completed(final Integer read_result, AsynchronousFileChannel attachment) {
        // if result is -1 means nothing was read.
        if (read_result != -1) {
            buffer.flip();

            if (pos == total)
                System.out.println("%" + ((float) pos / (float) total) * 100);

            socketchannel.write(buffer, socketchannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                @Override
                public void completed(Integer write_result, AsynchronousSocketChannel channel) {
                    if (write_result != -1) {
                        pos += write_result;
                        buffer.clear();
                        attachment.read(buffer, pos, attachment, current);

                    }
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                    System.out.println("Fail to write the message to server");
                }
            });


        }

        // initiate another asynchronous read, with this.


    }

    public void failed(Throwable exc,
                       AsynchronousFileChannel attachment) {
        System.err.println("Error!");
        exc.printStackTrace();
    }

    public void doit(FileUpload flh, AsynchronousSocketChannel socketChannel) {


        Path file = Paths.get(flh.getFullpath());

        AsynchronousFileChannel channel = null;
        this.socketchannel = socketChannel;


        try {
            channel = AsynchronousFileChannel.open(file);
        } catch (IOException e) {
            System.err.println("Could not open file: " + file.toString());

        }
        buffer = ByteBuffer.allocate(BUFFER_SIZE);
        total = flh.getFileSize();

        current = this;
        // start off the asynch read.
        channel.read(buffer, pos, channel, this);
        // this method now exits, thread returns to main and waits for user input.
    }


}
