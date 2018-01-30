package br.pucrio.inf.lac;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lac.cnclib.sddl.message.ApplicationMessage;
import lac.cnclib.sddl.message.ClientLibProtocol.PayloadSerialization;
import lac.cnclib.sddl.serialization.Serialization;
import lac.cnet.sddl.objects.ApplicationObject;
import lac.cnet.sddl.objects.Message;
import lac.cnet.sddl.objects.PrivateMessage;
import lac.cnet.sddl.udi.core.SddlLayer;
import lac.cnet.sddl.udi.core.UniversalDDSLayerFactory;
import lac.cnet.sddl.udi.core.UniversalDDSLayerFactory.SupportedDDSVendors;
import lac.cnet.sddl.udi.core.listener.UDIDataReaderListener;

public class CoreServer implements UDIDataReaderListener<ApplicationObject> {
	/** DEBUG */
	private static final String TAG = CoreServer.class.getSimpleName();

	/** SDDL Elements */
    private Object receiveMessageTopic;
    private Object toMobileNodeTopic;
    private static SddlLayer core;
    
    /** Mobile Hubs Data */
    private static final Map<UUID, UUID> mMobileHubs = new HashMap<>();
    
    /** Input reader */
    private static Scanner sc = new Scanner( System.in );

	//TODO: Change to you UUID
    private static final UUID uuid = UUID.fromString("71a3cb23-c608-482f-bc4b-86d09ff2cfbf");


    public static void main( String[] args ) {
		new CoreServer();

		mMobileHubs.put( uuid, uuid );

		final JsonObject mepaQuery = new JsonObject();
		final JsonObject options = new JsonObject();

		final String query1 = "INSERT INTO AVGHumidityTemperature " +
				"SELECT avg(humidity.sensorValue[0]) as avgHumidity, ( avg(temperature.sensorValue[0]) * 9/5 ) + 32 as avgTemperature " +
				"FROM SensorData(sensorName='Humidity').win:length(1000000) as humidity,  " +
				"SensorData(sensorName='Temperature').win:length(1000000) as temperature  " +
				"WHERE humidity.sensorName <> temperature.sensorName    " +
				"AND humidity.source = temperature.source ";

		final String query2 = "\"SELECT ( \" +\n" +
				"                        \"-43.379 \" + //c1\n" +
				"                        \"+ ( 2.049015233 * avgTemperature ) \" + //c2\n" +
				"                        \"+ ( 10.14333127 * avgHumidity ) \" + //c3\n" +
				"                        \"+ ( -0.22475541 * avgTemperature * avgHumidity ) \" + //c4\n" +
				"                        \"+ ( -6.837883 * 0.001 * avgTemperature * avgTemperature ) \" + //c5\n" +
				"                        \"+ ( -5.481717 * 0.01 * avgHumidity * avgHumidity ) \" + //c6\n" +
				"                        \"+ ( 1.22874 * 0.001 * avgTemperature * avgTemperature * avgHumidity ) \" + //c7\n" +
				"                        \"+ ( 8.5282 * 0.0001 * avgTemperature * avgHumidity * avgHumidity ) \" + //c8\n" +
				"                        \"+ ( -1.99 * 0.000001 * avgTemperature * avgTemperature * avgHumidity * avgHumidity )\" +//c9\n" +
				"                        \") as heatIndex \" +\n" +
				"                        \"FROM AVGHumidityTemperature\"";

		options.addProperty( "type", "add" );
		options.addProperty( "label", "AVGHumidityTemperature" );
		options.addProperty( "object", "rule" );
		options.addProperty( "rule", query1 );
		options.addProperty( "target", "local" );

		mepaQuery.add( "MEPAQuery", options );

		// Send the message
		final ApplicationMessage appMsg = new ApplicationMessage();
		appMsg.setPayloadType( PayloadSerialization.JSON );
		appMsg.setContentObject( "[" + mepaQuery.toString() + "]" );

		sendUnicastMSG( appMsg, uuid );

		options.remove("label");
		options.remove("rule");

		options.addProperty( "label", "HeatIndex" );
		options.addProperty( "rule", query2 );

		mepaQuery.remove( "MEPAQuery" );
		mepaQuery.add( "MEPAQuery", options );

		// Send the message
		final ApplicationMessage appMsg2 = new ApplicationMessage();
		appMsg.setPayloadType( PayloadSerialization.JSON );
		appMsg.setContentObject( "[" + mepaQuery.toString() + "]" );

		sendUnicastMSG( appMsg, uuid );

		System.out.println( "\nMessage sent! " );
	}
	
	/**
     * Constructor
     */
    private CoreServer() {
    	// Create a layer and participant
        core = UniversalDDSLayerFactory.getInstance( SupportedDDSVendors.OpenSplice );
        core.createParticipant( UniversalDDSLayerFactory.CNET_DOMAIN );
        // Receive and write topics to domain
        core.createPublisher();
        core.createSubscriber();
        // ClientLib Events
        receiveMessageTopic = core.createTopic( Message.class, Message.class.getSimpleName() );
        core.createDataReader( this, receiveMessageTopic );
        // To ClientLib
        toMobileNodeTopic = core.createTopic( PrivateMessage.class, PrivateMessage.class.getSimpleName() );
        core.createDataWriter( toMobileNodeTopic );
    }
    
    /**
     * Sends a message to all the components (BROADCAST)
     * @param appMSG The application message (e.g. a String message)
     */
    public static void sendBroadcastMSG( ApplicationMessage appMSG ) {
		PrivateMessage privateMSG = new PrivateMessage();
		privateMSG.setGatewayId( UniversalDDSLayerFactory.BROADCAST_ID );
		privateMSG.setNodeId( UniversalDDSLayerFactory.BROADCAST_ID );
		privateMSG.setMessage( Serialization.toProtocolMessage( appMSG ) );
		
		sendCoreMSG( privateMSG );
    }
    
    /**
     * Sends a message to a unique component (UNICAST)
     * @param appMSG The application message (e.g. a String message)
     * @param nodeID The UUID of the receiver
     */
    public static void sendUnicastMSG( ApplicationMessage appMSG, UUID nodeID ) {
		PrivateMessage privateMSG = new PrivateMessage();
		privateMSG.setGatewayId( UniversalDDSLayerFactory.BROADCAST_ID );
		privateMSG.setNodeId( nodeID );
		privateMSG.setMessage( Serialization.toProtocolMessage( appMSG ) );
		
		sendCoreMSG( privateMSG );
    }
    
    /**
     * Writes the message (send)
     * @param privateMSG The message
     */
    private static void sendCoreMSG( PrivateMessage privateMSG ) {
        core.writeTopic( PrivateMessage.class.getSimpleName(), privateMSG );
    }
    
    /**
     * Handle different events identified by a label
     * @param label The identifier of the event
     * @param data The data content of the event in JSON
     * @throws ParseException 
     */
    private void handleEvent( final String label, final JSONObject data ) throws ParseException {
    	
    	System.out.println( "\n===========================" );
    	
    	switch( label ) {
    		case "MaxAVG":
    			Double avg = (Double) data.get( "average" );
    			if( avg > 30 )
    				System.out.println( "Feels like hell!" );

                else if( avg >= 20 && avg <= 30 )
    				System.out.println( "The weather is perfect!" );

                else
    				System.out.println( "It is freezing here!" );
    		    break;

            case "HeatIndex":
                Double heat = ( Double ) data.get("value");

				String message = null;
                if( heat >= 80 && heat <= 90 ) {
					message = "Heat: Caution";

                } else if( heat > 90 && heat <= 105 ) {
					message = "Heat: Extreme Caution";

                } else if( heat > 105 && heat <= 130 ) {
					message = "Heat: Danger";

                } else if( heat > 130 ) {
					message = "Heat: Extreme Danger";

                }

                final ActuatorMessage actMessage = new ActuatorMessage();
				actMessage.setType( "act" );
				actMessage.setMessage( message );

				final Gson gson = new Gson();
				final String json = gson.toJson( actMessage );

				final ApplicationMessage appMessage = new ApplicationMessage();
				appMessage.setPayloadType( PayloadSerialization.JSON );
				appMessage.setContent( json.getBytes() );

				sendUnicastMSG( appMessage, uuid );
                System.out.println( heat );

                break;
    		
    		default:
    			break;
    	}
    	
    	System.out.println( "===========================\n" );
    }
    
    /**
     * Handle messages (e.g. error or reply)
     * @param object The JSONObject that contains the information
     * @throws ParseException 
     */
    private void handleMessage( final String tag, final JSONObject object ) throws ParseException {
    	final String component = (String) object.get( "component" );
		final String message   = (String) object.get( "message" );
		System.out.println( "\n>>" + tag + "(" + component + "): " + message + "\n" );
    }

	@Override
	public void onNewData( ApplicationObject topicSample ) {
		Message msg = null;
		
		if( topicSample instanceof Message ) {
			msg = (Message) topicSample;
			UUID nodeId = msg.getSenderId();
			UUID gatewayId = msg.getGatewayId();
			
			if( !mMobileHubs.containsKey( nodeId ) ){
				mMobileHubs.put( nodeId, gatewayId );
				System.out.println( ">>" + TAG + ": Client connected" );
			}
			
			String content = new String( msg.getContent() );
			JSONParser parser = new JSONParser();
			
			try {
	        	JSONObject object = (JSONObject) parser.parse( content );
	        	String tag = (String) object.get( "tag" );
	        	
	        	switch( tag ) {
	        		case "SensorData":
	        		break;
	        		
	        		case "EventData":
	        			final String label = (String) object.get( "label" );
                        final JSONObject jsonObject = ( JSONObject ) object.get("data");

	        			handleEvent( label, jsonObject );
		        	break;

					case "LocationData":
						final Double longitude = (Double) object.get("longitude");
						final Double latitude = (Double) object.get("latitude");

						final String latLong = String.format("Longitude: %s | Latitude: %s", longitude, latitude);
						System.out.println( latLong );

						break;
		        	
	        		case "ReplyData":		        	
	        		case "ErrorData":
	        			handleMessage( tag, object );
			        break;
	        	}
			} catch( Exception ex ) {
				System.out.println( ex.getMessage() );
			}
		}
	}
	
	/**
	 * A simple check to see if a string is a valid number 
	 * 
	 * @param s The number to be checked.
	 * @return true  It is a number.
	 *         false It is not a number.
	 */
	public static Boolean isNumber( String s ) {
		try {
            Integer.parseInt( s );
        }
		catch( NumberFormatException e ) {
			return false;			
		}
		return true;
	}
}
