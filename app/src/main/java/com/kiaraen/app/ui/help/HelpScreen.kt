package com.kiaraen.app.ui.help

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.BuildConfig
import com.kiaraen.app.R
import com.kiaraen.app.ui.kiara.AccordionRow
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.OutlineButton
import com.kiaraen.app.ui.theme.Charcoal
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import androidx.compose.foundation.layout.widthIn

/**
 * راهنما و پشتیبانی — the approved #help screen: the dark charcoal hero
 * («راهنمای شروع سریع»)، the 4-question FAQ accordion with real answers,
 * the support card and the two contact buttons + version footer.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun mail(subject: String) {
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@kiara.app"))
                putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "تماس با پشتیبانی"))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
        ) {

            Spacer(Modifier.height(14.dp))
            DetailHeader(title = "راهنما و پشتیبانی", onBack = onBack)

            Spacer(Modifier.height(6.dp))
            // dark hero
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Charcoal, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(Gold, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ki_question), null, Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("راهنمای شروع سریع", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Gold)
                        Text(
                            "هر چیزی که برای روزهای اول لازم داری",
                            fontSize = 11.sp,
                            color = Color(0xFFCFC9BD),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("سوال‌های پرتکرار", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Muted)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccordionRow(
                    question = "چطور هر روز به هدفم برسم؟",
                    answer = "از صفحهٔ خانه کارت «ادامه یادگیری» را باز کن و روزی یک درس فلش‌کارت بزن؛ بعد «وقت مرور رسیده» را جواب بده. با هدف ۵ تا ۳۰ واژه‌ای، حدود ۳ تا ۱۵ دقیقه وقت می‌برد.",
                )
                AccordionRow(
                    question = "سکوی طلا چه کاری داره؟",
                    answer = "هر پاسخ درست سکه می‌دهد (تمرین ۵ سکه، آزمون ۵ سکه). سکه‌ها معیار پیشرفت‌اند و در صفحهٔ پیشرفت و نشان‌ها دیده می‌شوند.",
                )
                AccordionRow(
                    question = "چرا بعضی واژه‌ها دائم مرور می‌شوند؟",
                    answer = "کیارا از تکرار فاصله‌دار (SRS) استفاده می‌کند: واژه‌های اشتباه زودتر برمی‌گردند تا سر جایشان بنشینند. دکمهٔ «فردا» آن واژه را به فردا موکول می‌کند.",
                )
                AccordionRow(
                    question = "استریک روزانه چطور حفظ می‌شود؟",
                    answer = "هر روز که حداقل یک تمرین انجام بدهی، شعلهٔ استریک روشن می‌ماند. با ورود به حساب، استریک و پیشرفتت بین دستگاه‌ها همگام می‌شود.",
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("تماس با ما", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Muted)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(22.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("پشتیبانی kiara", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
                        Text(
                            "پاسخی در کمتر از ۲۴ ساعت",
                            fontSize = 11.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Image(painterResource(R.drawable.ki_chat), null, Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton(
                    text = "ارسال بازخورد",
                    onClick = { mail("بازخورد کیارا") },
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                ClayButton(
                    text = "گفتگو آنلاین",
                    onClick = { mail("سوال از پشتیبانی کیارا") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "kiara · نسخهٔ ${BuildConfig.VERSION_NAME} — ساخته‌شده برای یادگیری شیرین‌تر",
                fontSize = 11.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(26.dp))
    
        }
    }
}
