package org.owntracks.android.support;

import com.fasterxml.jackson.core.JsonParseException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.owntracks.android.App;
import org.owntracks.android.messages.MessageBase;
import org.owntracks.android.messages.MessageLocation;
import org.owntracks.android.messages.MessageUnknown;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;


@RunWith(PowerMockRunner.class)
@PrepareForTest({App.class})
public class ParserTest {
    private MessageLocation messageLocation;
    @Mock
    private
    Preferences testPreferences;

    @Before
    public void setupMessageLocation() {
        messageLocation = new MessageLocation();
        messageLocation.setAcc(10);
        messageLocation.setAlt(20);
        messageLocation.setBatt(30);
        messageLocation.setConn("TestConn");
        messageLocation.setLat(50.1);
        messageLocation.setLon(60.2);
        messageLocation.setTst(123456789);
    }

    @Before
    public void setupEncryptionProvider() {
        mockStatic(App.class);
        org.powermock.api.mockito.PowerMockito.when(App.getPreferences()).thenReturn(testPreferences);
        when(testPreferences.getEncryptionKey()).thenReturn("testEncryptionKey");
        when(encryptionProvider.isPayloadEncryptionEnabled()).thenReturn(true);
    }

    @Test
    public void ParserCorrectlyConvertsLocationToPrettyJSON() throws Exception {
        Parser parser = new Parser(null);
        String expected = "{\n" +
                "  \"_type\" : \"location\",\n" +
                "  \"batt\" : 30,\n" +
                "  \"acc\" : 10,\n" +
                "  \"vac\" : 0,\n" +
                "  \"lat\" : 50.1,\n" +
                "  \"lon\" : 60.2,\n" +
                "  \"alt\" : 20.0,\n" +
                "  \"tst\" : 123456789,\n" +
                "  \"conn\" : \"TestConn\"\n" +
                "}";
        assertEquals(expected, parser.toJsonPlainPretty(messageLocation));
    }

    @Test
    public void ParserCorrectlyConvertsLocationToPlainJSON() throws Exception {
        Parser parser = new Parser(null);
        String expected = "{\"_type\":\"location\",\"batt\":30,\"acc\":10,\"vac\":0,\"lat\":50.1,\"lon\":60.2,\"alt\":20.0,\"tst\":123456789,\"conn\":\"TestConn\"}";
        assertEquals(expected, parser.toJsonPlain(messageLocation));
    }

    // Need to mock this as we can't unit test native lib off-device
    @Mock
    private
    EncryptionProvider encryptionProvider;

    @Test
    public void ParserCorrectlyConvertsLocationToJSONWithEncryption() throws Exception {
        when(encryptionProvider.encrypt(anyString())).thenReturn("TestCipherText");

        Parser parser = new Parser(encryptionProvider);
        String expected = "{\"_type\":\"encrypted\",\"data\":\"TestCipherText\"}";
        assertEquals(expected, parser.toJson(messageLocation));
    }

    @Test
    public void ParserCorrectlyConvertsLocationToJSONWithEncryptionDisabled() throws Exception {
        when(encryptionProvider.isPayloadEncryptionEnabled()).thenReturn(false);
        when(encryptionProvider.encrypt(anyString())).thenReturn("TestCipherText");

        Parser parser = new Parser(encryptionProvider);
        String expected = "{\"_type\":\"location\",\"batt\":30,\"acc\":10,\"vac\":0,\"lat\":50.1,\"lon\":60.2,\"alt\":20.0,\"tst\":123456789,\"conn\":\"TestConn\"}";
        assertEquals(expected, parser.toJson(messageLocation));
    }


    @Test
    public void ParserReturnsMessageLocationFromValidLocationInput() throws Exception {
        Parser parser = new Parser(encryptionProvider);
        String input = "{\"_type\":\"location\",\"tid\":\"s5\",\"acc\":1600,\"alt\":0.0,\"batt\":99,\"conn\":\"w\",\"lat\":52.3153748,\"lon\":5.0408462,\"t\":\"p\",\"tst\":1514455575,\"vac\":0}";
        MessageBase messageBase = parser.fromJson(input);
        assertEquals(MessageLocation.class, messageBase.getClass());
        MessageLocation messageLocation = (MessageLocation) messageBase;
        assertEquals(1514455575L, messageLocation.getTst());
        assertEquals("s5", messageLocation.getTid());
        assertEquals(1600, messageLocation.getAcc());
        assertEquals(0.0, messageLocation.getAlt(), 0);
        assertEquals(99, messageLocation.getBatt());
        assertEquals("w", messageLocation.getConn());
        assertEquals(52.3153748, messageLocation.getLatitude(), 0);
        assertEquals(5.0408462, messageLocation.getLongitude(), 0);
        assertEquals("p", messageLocation.getT());
        assertEquals(0.0, messageLocation.getVac(), 0);
    }

    @Test
    public void ParserReturnsMessageLocationFromValidEncryptedLocationInput() throws Exception {
        when(encryptionProvider.isPayloadEncryptionEnabled()).thenReturn(true);
        String messageLocationJSON = "{\"_type\":\"location\",\"tid\":\"s5\",\"acc\":1600,\"alt\":0.0,\"batt\":99,\"conn\":\"w\",\"lat\":52.3153748,\"lon\":5.0408462,\"t\":\"p\",\"tst\":1514455575,\"vac\":0}";
        when(encryptionProvider.decrypt("TestCipherText")).thenReturn(messageLocationJSON);

        Parser parser = new Parser(encryptionProvider);
        String input = "{\"_type\":\"encrypted\",\"data\":\"TestCipherText\"}";
        MessageBase messageBase = parser.fromJson(input);
        assertEquals(MessageLocation.class, messageBase.getClass());
        MessageLocation messageLocation = (MessageLocation) messageBase;
        assertEquals(1514455575L, messageLocation.getTst());
        assertEquals("s5", messageLocation.getTid());
        assertEquals(1600, messageLocation.getAcc());
        assertEquals(0.0, messageLocation.getAlt(), 0);
        assertEquals(99, messageLocation.getBatt());
        assertEquals("w", messageLocation.getConn());
        assertEquals(52.3153748, messageLocation.getLatitude(), 0);
        assertEquals(5.0408462, messageLocation.getLongitude(), 0);
        assertEquals("p", messageLocation.getT());
        assertEquals(0.0, messageLocation.getVac(), 0);
    }

    @Test(expected = Parser.EncryptionException.class)
    public void ParserShouldThrowExceptionWhenGivenEncryptedMessageWithEncryptionDisabled() throws Exception {
        when(encryptionProvider.isPayloadEncryptionEnabled()).thenReturn(false);
        Parser parser = new Parser(encryptionProvider);
        String input = "{\"_type\":\"encrypted\",\"data\":\"TestCipherText\"}";
        parser.fromJson(input);
    }

    @Test
    public void ParserShouldDecodeStreamOfMultipleMessageLocationsCorrectly() throws Exception {
        String multipleMessageLocationJSON = "[{\"_type\":\"location\",\"tid\":\"s5\",\"acc\":1600,\"alt\":0.0,\"batt\":99,\"conn\":\"w\",\"lat\":52.3153748,\"lon\":5.0408462,\"t\":\"p\",\"tst\":1514455575,\"vac\":0},{\"_type\":\"location\",\"tid\":\"s5\",\"acc\":95,\"alt\":0.0,\"batt\":99,\"conn\":\"w\",\"lat\":12.3153748,\"lon\":15.0408462,\"t\":\"p\",\"tst\":1514455579,\"vac\":0}]";
        Parser parser = new Parser(encryptionProvider);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(multipleMessageLocationJSON.getBytes());
        MessageBase[] messages = parser.fromJson(byteArrayInputStream);
        assertEquals(2, messages.length);
        for (MessageBase messagebase : messages) {
            assertEquals(MessageLocation.class, messagebase.getClass());
        }
        MessageLocation firstMessageLocation = (MessageLocation) messages[0];
        assertEquals(1514455575L, firstMessageLocation.getTst());
        MessageLocation secondMessageLocation = (MessageLocation) messages[1];
        assertEquals(1514455579L, secondMessageLocation.getTst());
    }

    @Test(expected = IOException.class)
    public void ParserShouldThrowExceptionOnEmptyArray() throws Exception {
        Parser parser = new Parser(encryptionProvider);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[0]);
        parser.fromJson(byteArrayInputStream);
    }

    @Test
    public void ParserReturnsMessageUnknownOnValidOtherJSON() throws Exception {
        Parser parser = new Parser(encryptionProvider);
        MessageBase message = parser.fromJson("{\"some\":\"invalid message\"}");
        assertEquals(MessageUnknown.class,message.getClass());
    }

    @Test(expected = JsonParseException.class)
    public void ParserThrowsCorrectExceptionWhenGivenInvalidJSON() throws Exception {
        Parser parser = new Parser(encryptionProvider);
        MessageBase message = parser.fromJson("not JSON");
    }
}