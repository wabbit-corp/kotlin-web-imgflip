package one.wabbit.web.imgflip

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// https://imgflip.com/api
class ImgflipApi(val httpClient: HttpClient) {
    // {
    //   "success": true,
    //   "data": {
    //      "memes": [
    //         {
    //            "id": "61579",
    //            "name": "One Does Not Simply",
    //            "url": "https://i.imgflip.com/1bij.jpg",
    //            "width": 568,
    //            "height": 335,
    //            "box_count": 2
    //         },
    //         {
    //            "id": "101470",
    //            "name": "Ancient Aliens",
    //            "url": "https://i.imgflip.com/26am.jpg",
    //            "width": 500,
    //            "height": 437,
    //            "box_count": 2
    //         }
    //         // probably a lot more memes here..
    //      ]
    //   }
    //}

    @Serializable
    data class Meme(
        val id: String,
        val name: String,
        val url: String,
        val width: Int,
        val height: Int,
        @SerialName("box_count") val boxCount: Int,
        val captions: Long
    )

    @Serializable
    data class Credentials(
        val username: String,
        val password: String
    )

    class ServiceException(message: String) : Exception(message)

    suspend fun getAllMemes(): List<Meme> {
        @Serializable
        data class MemeList(
            val memes: List<Meme>
        )

        @Serializable
        data class MemeResponse(
            val success: Boolean,
            val data: MemeList
        )

        val response = httpClient.get("https://api.imgflip.com/get_memes")
        val bodyAsText = response.bodyAsText()
        val allMemes = Json.decodeFromString<MemeResponse>(bodyAsText)
        return allMemes.data.memes
    }

    suspend fun generateMeme(memeId: String, boxText: List<String>, credentials: Credentials): String {
        val data = mutableMapOf(
            "template_id" to memeId,
            "username" to credentials.username,
            "password" to credentials.password
        )

        for ((i, text) in boxText.withIndex()) {
            data["boxes[$i][text]"] = text
        }

        val response = httpClient.submitForm(
            url = "https://api.imgflip.com/caption_image",
            formParameters = parameters {
                data.forEach { (k, v) -> append(k, v) }
            }
        )

        val bodyAsText = response.bodyAsText()

        // Example Success Response:
        //{
        //   "success": true,
        //   "data": {
        //      "url": "https://i.imgflip.com/123abc.jpg",
        //      "page_url": "https://imgflip.com/i/123abc"
        //   }
        //}
        //
        //Example Failure Response:
        //{
        //   "success" => false,
        //   "error_message" => "Some hopefully-useful statement about why it failed"
        //}

        val json = Json.decodeFromString<JsonObject>(bodyAsText)

        if (!json["success"]!!.jsonPrimitive.boolean) {
            throw ServiceException(json["error_message"]!!.jsonPrimitive.content)
        }

        val url = json["data"]!!.jsonObject["url"]!!.jsonPrimitive.content

        return url
    }
}
