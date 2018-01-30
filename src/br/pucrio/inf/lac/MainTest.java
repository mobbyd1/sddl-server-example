package br.pucrio.inf.lac;

import lac.cnclib.net.mrudp.MrUdpNodeConnection;
import lac.cnclib.sddl.message.ApplicationMessage;
import lac.cnclib.sddl.message.Message;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by ruhan on 12/15/16.
 */
public class MainTest {

    public static void main( String args[] ) throws IOException {
        MrUdpNodeConnection connection = new MrUdpNodeConnection();

        final InetSocketAddress inetSocketAddress = new InetSocketAddress("192.168.0.101", 5500);
        connection.connect( inetSocketAddress );

        final Message msg = new ApplicationMessage();
        msg.setContent("teste".getBytes());

        connection.sendMessage( msg );

    }
}
