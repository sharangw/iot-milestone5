package edu.utexas.mpc.samplemqttandroidapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
//import com.android.provider.Settings
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    var weatherData:String = ""
    var currentWeatherData:String = ""
    var forecastWeatherData:String = ""

    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var tempText: TextView
    lateinit var descriptionText: TextView
    lateinit var city: TextView
    lateinit var steps: TextView
    lateinit var imageView: ImageView
    lateinit var retrieveButton: Button
    lateinit var syncButton: Button
    lateinit var monthButton: Button

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var weatherByZip: WeatherByZip
    lateinit var weatherForecast: WeatherForecast
    lateinit var context: Context


    // I'm doing a late init here because I need this to be an instance variable but I don't
    // have all the info I need to initialize it yet
    lateinit var mqttAndroidClient: MqttAndroidClient

    // you may need to change this depending on where your MQTT broker is running
    val serverUri = "tcp://192.168.4.1:1883"
    // you can use whatever name you want to here
    val clientId = "EmergingTechMQTTClient"

    //these should "match" the topics on the "other side" (i.e, on the Raspberry Pi)
    val subscribeTopic = "testTopic2"
    val publishTopic = "testTopic1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tempText = this.findViewById(R.id.tempText)
        descriptionText = this.findViewById(R.id.description)
        steps = this.findViewById(R.id.steps)
        city = this.findViewById(R.id.city)
        imageView = this.findViewById(R.id.icon)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        syncButton = this.findViewById(R.id.syncButton)
        monthButton = this.findViewById(R.id.monthButton)
        context = this.baseContext

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        // when the user presses the get weather button, this method will get called
        retrieveButton.setOnClickListener({ requestWeather() })

        // when the user presses the syncbutton, this method will get called
        syncButton.setOnClickListener({ sendWeatherData() })

        // when user presses the month button, then call method to get monthly goals and display in on Pi
        monthButton.setOnClickListener({ calculateMonthlyGoals() })

        // initialize the paho mqtt client with the uri and client id
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("onCreate() - Connection Complete!!")

                mqttAndroidClient.subscribe(subscribeTopic, 0)

                val message = MqttMessage()

                message.payload = ("IoTexas Steps").toByteArray()

                message.payload = (weatherData).toByteArray()
                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
                println("published message")

            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String, message: MqttMessage) {
                println("onCreate() - Message Arrived")

                try {
                    val data = String(message.payload, charset("UTF-8"))
                    println("onCreate() Data from Pi: " + data)
                    steps.setText(data)

                } catch (e:Exception) {}


            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })

    }

    /**
     * This method makes two calls to the weather API:
     * One to get the current weather related data to display on the screen and send to the Raspberry Pi
     * Other to get the forecast related data to send to the Raspberry Pi
     */
    fun requestWeather(){

        // user entered zipcode
        var zip = zipcode.text
        println("zipcode: " + zip)

        // url for current weather by zipcode
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?zip="+zip+",us&appid=afabe25850681def136db573945218f8").toString()

        // make first API call
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    println(response)
                    println("${response::class.qualifiedName}") // string

                    // map API response to Kotlin class
                    weatherByZip = gson.fromJson(response, WeatherByZip::class.java)

                    // get precipitation values
                    var rain: Double? = checkRain(weatherByZip)
                    var snow: Double? = checkSnow(weatherByZip)

                    // Convert temperature units and format
                    val lowTempRounded = convertTemperature(weatherByZip.main.temp_min)
                    val displayTempRounded = convertTemperature(weatherByZip.main.temp)
                    val highTempRounded = convertTemperature(weatherByZip.main.temp_max)

                    // Display weather data on app
                    val roundedDisplay = displayTempRounded.toString() + " F"
                    tempText.text = roundedDisplay

                    val description : String = weatherByZip.weather.get(0).description
                    descriptionText.text = description

                    val cityName : String = weatherByZip.name
                    city.text = cityName

                    // Display weather icon
                    showWeatherIcon(weatherByZip)

                    // Create json object of weather data to send to Raspberry Pi
                    prepareCurrentWeatherData(displayTempRounded, lowTempRounded, highTempRounded, rain, snow)

                },
                com.android.volley.Response.ErrorListener { println("******Not able to get current weather!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)


        // url for forecasted weather by zipcode
        val forecasturl = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?zip="+zip+",us&appid=afabe25850681def136db573945218f8").toString()

        // make second API call
        val stringRequestForecast = object : StringRequest(com.android.volley.Request.Method.GET, forecasturl,
                com.android.volley.Response.Listener<String> { response ->
                    println(response)
                    println("${response::class.qualifiedName}") // string

                    // map API response to Kotlin class
                    weatherForecast= gson.fromJson(response, WeatherForecast::class.java)

                    // get tomorrow date
                    val tomorrowDate = getTomorrowDate()

                    // declare lists to hold multiple temperature/precipitation values
                    val temps = ArrayList<Long>()
                    val minTemps = ArrayList<Long>()
                    val maxTemps = ArrayList<Long>()
                    val rains = ArrayList<Double>()
                    var snows = ArrayList<Double>()

                    var day = 0
                    for (i in weatherForecast.list) { // iterate through all the forecasts for next 5 days

                        // get the current day's weather details object from the list of forecast
                        val weatherDetails = weatherForecast.list.get(day)
                        var date = weatherDetails.dt_txt.substring(0,10)

                        if (date.equals(tomorrowDate.substring(0,10))) { // only use data from tomorrow's forecasts

                            println("Tomorrow: " + date)

                            // append to metric to appropriate list:

                            var temp = convertTemperature(weatherDetails.main.temp)
                            var temp_min = convertTemperature(weatherDetails.main.temp_min)
                            var temp_max = convertTemperature(weatherDetails.main.temp_max)

                            temps.add(temp)
                            minTemps.add(temp_min)
                            maxTemps.add(temp_max)

                            var rainForecasted: Double = checkRainForecast(weatherForecast, day)
                            var snowForecasted: Double = checkSnowForecast(weatherForecast, day)

                            rains.add(rainForecasted)
                            snows.add(snowForecasted)

                        }

                        day = day+1
                    }

                    // compute averages of all lists
                    val averageTemp : Long = calculateAverage(temps)
                    val averageMinTemp : Long? = minTemps.min()
                    val averageMinTempLong : Long = averageMinTemp!!.toLong()
                    val averageMaxTemp : Long? = maxTemps.max()
                    val averageMaxTempLong : Long = averageMaxTemp!!.toLong()

                    val averageRain : Double = calculateAverageDouble(rains)
                    val averageSnow : Double = calculateAverageDouble(snows)

                    // Create json object of forecasted weather data to send to Raspberry Pi
                    prepareForecastWeatherData(averageTemp, averageMinTempLong, averageMaxTempLong, averageRain, averageSnow)

                    // combine two sets of weather data
                    weatherData = currentWeatherData + forecastWeatherData
                    println("complete weather data: " + weatherData)

                },
                com.android.volley.Response.ErrorListener { println("******Not able to get forecast!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequestForecast)


        val wifiRequest = object : StringRequest(com.android.volley.Request.Method.GET, forecasturl,
                com.android.volley.Response.Listener<String> { response ->
                    Thread.sleep(2000)
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS));
                },
        com.android.volley.Response.ErrorListener { println("******") }) {}
        queue.add(wifiRequest)
    }

    /**
     * This method sends the weather data by publishing to the testtopic
     */
    fun sendWeatherData() {

        // connects the paho mqtt client to the broker
        mqttAndroidClient.connect() // sync with pi
        println("############### Connecting to PI.....")

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {


            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("############### sendWeatherData() - Connection Complete!!")

                mqttAndroidClient.subscribe(subscribeTopic, 0)

                val message = MqttMessage()

                message.payload = ("IoTexas Steps").toByteArray()

                message.payload = (weatherData).toByteArray()
                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
                println("############### sendWeatherData()- published message")

            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String, message: MqttMessage) {
                println("############### sendWeatherData() - Message Arrived")

                try {
                    val data = String(message.payload, charset("UTF-8"))
                    println("############### sendWeatherData() Data from Pi: " + data)
                    steps.setText(data)

                    mqttAndroidClient.disconnect()
                    println("############### Disconnected from Pi")
                } catch (e:Exception) {}


            }

            override fun connectionLost(cause: Throwable?) {
                println("############### Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("############### Delivery Complete")
            }
        })
    }

    /**
     * This method will let the Raspberry Pi know to print to the terminal the daily readjusted steps goals according to the new model
     * for the entire month (or however much synthetic steps/weather data there is)
     */
    fun calculateMonthlyGoals() {
        println("############### Calculating Monthly Goals...")

        // connects the paho mqtt client to the broker
        mqttAndroidClient.connect() // sync with pi
        println("############### calculateMonthlyGoals()- Connecting to PI.....")

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("############### calculateMonthlyGoals() - Connection Complete!!")

                mqttAndroidClient.subscribe(subscribeTopic, 0)

                val message = MqttMessage()

                message.payload = ("IoTexas Steps").toByteArray()

                message.payload = ("month").toByteArray()  // the pi will check if this message (starting with month) was sent and will display info accordingly
                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
                val messeageSent = message.payload.toString()
                println("############### calculateMonthlyGoals()- published message")

            }

            // this method is called when a message is received that fulfills a subscription

            // Should not be used for now
            override fun messageArrived(topic: String, message: MqttMessage) {
                println("############### calculateMonthlyGoals() - Message Arrived")

                try {
                    val data = String(message.payload, charset("UTF-8"))
                    println("############### calculateMonthlyGoals() Data from Pi: " + data)
                    steps.setText(data)

                    mqttAndroidClient.disconnect()
                    println("############### Disconnected from Pi")
                } catch (e:Exception) {}


            }

            override fun connectionLost(cause: Throwable?) {
                println("############### Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("############### Delivery Complete")
            }
        })

    }


    /**
     * calculate average of arraylist of longs
     */
    fun calculateAverage(numbers:ArrayList<Long>) : Long {

        var sum : Long = 0

        for (i in numbers) {
            sum += i
        }

        var average : Long = sum/numbers.size
        return average
    }

    /**
     * calculate average of arraylist of longs
     */
    fun calculateAverageDouble(numbers:ArrayList<Double>) : Double {

        var sum : Double = 0.0

        for (i in numbers) {
            sum += i
        }

        var average : Double = sum/numbers.size
        return average
    }


    /**
     * get the current system date and calculate tomorrow's date
     */
    fun getTomorrowDate() : String  {
        var calendar = Calendar.getInstance() // get current date
        calendar.add(Calendar.DATE, 1)
        val tomorrow: Date = calendar.time
        val sdf = SimpleDateFormat("YYYY-MM-dd hh:mm:ss")
        val tomorrowDate = sdf.format(tomorrow)
        println("Tomorrow: " + tomorrowDate)
        return tomorrowDate
    }


    /**
     * Check if there is rainfall and return 0 if none
     */
    fun checkRain(weather: WeatherByZip) : Double? {
        var rain: Double?

        try {
            rain = weather.rain.onehour
            println("rain: " + rain)
        } catch (e:Exception) {
            println("no rain !!! ")
            rain = 0.0
        }

        return rain

    }

    /**
     * Check if there is rainfall in the forecast and return 0 if none
     */
    fun checkRainForecast(weatherForecast: WeatherForecast, day: Int) : Double {
        var rain: Double

        try {
            rain = weatherForecast.list.get(day).rain.threehour
            println("rain forecast on day " + day + ": " + rain)
        } catch (e:Exception) {
            println("no rain on day " + day + "!!! ")
            rain = 0.0
        }

        return rain

    }

    /**
     * Check is there is snowfall and return 0 if none
     */

    fun checkSnow(weather: WeatherByZip) : Double? {
        var snow: Double?

        try {
            snow = weather.snow.onehour
            println("snow: " + snow)
        } catch (e:java.lang.Exception){
            println("no snow !!! ")
            snow = 0.0
        }
        return snow
    }

    /**
     * Check if there is snowfall in the forecast and return 0 if none
     */
    fun checkSnowForecast(weatherForecast: WeatherForecast, day: Int) : Double {
        var snow: Double

        try {
            snow = weatherForecast.list.get(day).snow.threehour
            println("snow forecast on day " + day + ": " + snow)
        } catch (e:Exception) {
            println("no snow on day " + day + "!!! ")
            snow = 0.0
        }

        return snow

    }

    /**
     * Create json object of current weather data to send to Raspberry Pi
     */
    fun prepareCurrentWeatherData(mean:Long, low:Long, high: Long, rain:Double?, snow:Double?) {

        currentWeatherData = "{\"temp\":" + mean + ", \"low\":" + low + ", \"high\": " + high +
                ", \"rain\":" + rain + ", \"snow\":" + snow
        println(currentWeatherData)

    }

    /**
     * Create json object of forecast weather data to send to Raspberry Pi
     */
    fun prepareForecastWeatherData(mean:Long, low:Long, high: Long, rain:Double?, snow:Double?) {

        forecastWeatherData = ", \"forecastTemp\":" + mean + ", \"forecastLow\":" + low + ", \"forecastHigh\": " + high +
                ", \"forecastRain\":" + rain + ", \"forecastSnow\":" + snow + "}"
        println(forecastWeatherData)

    }

    /**
     * Displays the weather icon
     */
    fun showWeatherIcon(weatherData:WeatherByZip) {
        val iconCode = weatherByZip.weather.get(0).icon
        val iconUrl = "https://openweathermap.org/img/w/"+iconCode+".png"
        Picasso.get().load(iconUrl).into(imageView);
    }


    /**
     * Takes a temperature in Kelvin and returns it in Fahrenheit rounded to nearest whole number
     */
    fun convertTemperature  (tempKelvin:Double) : Long {

        var fahrenheitTemp : Double = (tempKelvin - 273.15) * (9/5) + 32
        val fahrenheitRounded = Math.round(fahrenheitTemp)
        return fahrenheitRounded
    }


    // Map the response from the API (current weather by zipcode to a class):
    class WeatherByZip(val coord: Coordinates, val weather: Array<Weather>, val base: String,
                       val main: WeatherMain, val visibility: Int, val wind: Wind, val clouds: Clouds,
                       val dt: Int, val sys: Sys, val timezone: Int, val id: Int, val name: String, val cod: Int,
                       val rain: Rain, val snow: Snow)
    class Wind(val speed: Double, val deg: Int)
    class Clouds(val all: Int)
    class Rain( @SerializedName("3h") val threehour : Double?, @SerializedName("1h") val onehour : Double?)
    class Snow( @SerializedName("3h") val threehour : Double?, @SerializedName("1h") val onehour : Double?)
    class RainForecast( @SerializedName("3h") val threehour : Double)
    class SnowForecast( @SerializedName("3h") val threehour : Double)
    class Sys(val type: Int, val id: Int, val message: Double, val country: String, val sunrise: Int, val sunset: Int)
    class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
    class Coordinates(val lon: Double, val lat: Double)
    class Weather(val id: Int, val main: String, val description: String, val icon: String)
    class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

    // Map the response from the API (weather forecast for 5 days every 3 hours):
    class WeatherForecast(val cod: String, val message: Int, val cnt: Int, val list: Array<WeatherDetails>, val city: City)
    class City(val name: String, val coord: Coordinates, val country: String, val timezone: Int, val sunrise: Int, val sunset: Int)
    class WeatherDetails(val dt: Int, val main: WeatherMain, val weather: Array<Weather>, val clouds: Clouds, val wind: Wind,
                         val rain: RainForecast, val snow: SnowForecast, val sys: Sys2, val dt_txt: String)
    class Sys2(val pod: String)


}
