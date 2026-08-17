package com.example.parentingmonitoringapp

import android.os.AsyncTask
import android.util.Log
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailSender {

    // ⚠️ Palitan ito ng sarili mong Gmail at App Password
    private const val SENDER_EMAIL = "parentmonitoringtcu@gmail.com"
    private const val SENDER_APP_PASSWORD = "xtjdiwcovgcarnje" // walang space, 16 characters

    fun sendEmail(toEmail: String, subject: String, body: String) {
        Thread {
            try {
                val props = Properties()
                props["mail.smtp.host"] = "smtp.gmail.com"
                props["mail.smtp.port"] = "587"
                props["mail.smtp.auth"] = "true"
                props["mail.smtp.starttls.enable"] = "true"

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(SENDER_EMAIL, SENDER_APP_PASSWORD)
                    }
                })

                val message = MimeMessage(session)
                message.setFrom(InternetAddress(SENDER_EMAIL))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                message.subject = subject
                message.setText(body)

                Transport.send(message)
                Log.d("EmailSender", "Email sent successfully to $toEmail")

            } catch (e: Exception) {
                Log.e("EmailSender", "Failed to send email: ${e.message}")
            }
        }.start()
    }
}