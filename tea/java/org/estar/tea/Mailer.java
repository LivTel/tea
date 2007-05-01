package org.estar.tea;

import java.util.*;
import java.net.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

public class Mailer {

    private String smtpHost;

    private String mailToAddr;

    private String mailFromAddr;

    private String mailCcAddr;

    public Mailer(String smtpHost) {
	this.smtpHost = smtpHost;
	Properties props = System.getProperties();
	props.put("mail.smtp.host", smtpHost);	
    }

    public void setSmtpHost(String h) {this.smtpHost = h;}

    public void setMailToAddr(String a) {this.mailToAddr = a;}

    public void setMailFromAddr(String a) {this.mailFromAddr = a;} 
    
    public void setMailCcAddr(String a) {this.mailCcAddr = a;}

    public void send(String text) throws Exception {
	Properties props = System.getProperties();
	Session session = Session.getDefaultInstance(props, null);
	// -- Create a new message --
	Message msg = new MimeMessage(session);
	// -- Set the FROM and TO fields --
	msg.setFrom(new InternetAddress(mailFromAddr));
	msg.setRecipients(Message.RecipientType.TO,
			  InternetAddress.parse(mailToAddr, false));
	msg.setRecipients(Message.RecipientType.CC,
			  InternetAddress.parse(mailCcAddr, false));

	// -- Set the subject and body text --
	msg.setSubject("TEA Error");
	msg.setText(text);
	// -- Set some other header information --
	msg.setHeader("X-Mailer", "JavaMailOnProxy");
	msg.setSentDate(new Date());
	// -- Send the message --
	Transport.send(msg);
	System.out.println("Mail Message sent OK.");
    }

    public static void main(String args[]) {

	try {
			
	    String smtpHost = args[0];	 
	    Mailer mailer = new Mailer(smtpHost);
	    String mailToAddr = args[1];
	    mailer.setMailToAddr(mailToAddr);
	    String mailFromAddr = args[2]; 
	    mailer.setMailFromAddr(mailFromAddr);
	    String mailCcAddr = args[3]; 
	    mailer.setMailCcAddr(mailCcAddr);	

	    String text = "A short test message from the TEA";

	    mailer.send(text);

	} catch (Exception e) {
	    System.err.println("Mailer mailHost to from cc ");
	    e.printStackTrace();
	}
    }

}
