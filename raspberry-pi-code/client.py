import paho.mqtt.client as mqtt
import time

import aioblescan as aiobs
from aioblescan.plugins import EddyStone
import asyncio
import json

stepMessage = ""
numOfSteps = 7
steps = 0
broker_address = "192.168.4.1" #enter your broker address here
subscribetopic = "testTopic1"
publishtopic = "testTopic2"
client = mqtt.Client("P1")
client.connect(broker_address)

def on_message(client, userdata, message):

  global numOfSteps
  global stepMessage
  
  print("----------inside on message")  
  print("message received ", message.payload.decode("utf-8"))
  dict_weather = json.loads(message.payload.decode("utf-8"))
  
  # Steps today
  low = dict_weather["low"]
  high = dict_weather["high"]
  rain = dict_weather["rain"]
  snow = dict_weather["snow"]
  precipitation = convert_precip(snow,rain)
  steps_today = convert_steps(high,low,precipitation)
  print("Number of steps today is ",steps_today)
  
  #Steps Tomorrow
  forecast_low = dict_weather["forecastLow"]
  forecast_high = dict_weather["forecastHigh"]
  forecast_rain = dict_weather["forecastRain"]
  forecast_snow = dict_weather["forecastSnow"] 
  forecast_precipitation = convert_precip(forecast_snow,forecast_rain) 
  steps_tomorrow = convert_steps(forecast_high,forecast_low,forecast_precipitation)  
  print("Number of steps tomorrow ",steps_tomorrow)
  
  actualSteps = getSteps()
  actualSteps = int(actualSteps) * 1000
  print("Actual Steps ", actualSteps)
  print(type(actualSteps))
  
  userRec = ""
  if actualSteps > steps_today:
      userRec = "Good job! "
  else:
      userRec = "Keep walking, fatty! "
  
  userRec += "\nYou should walk {} steps tomorrow!".format(steps_tomorrow)
  
  print(userRec)
  client.publish(publishtopic, userRec)
  print("published")  
  time.sleep(5)

def convert_steps(high_weather,low_weather,precipitation):
    b0,b1,b2,b3 = 1725.18550495,41.73728844,16.29751196,553.50462998
    value = b0 + b1 * high_weather + b2 * low_weather + b3 * precipitation
    return int(value)

def convert_precip(snow,rain):
    val = 0 
    if snow > 0 and rain > 0:
        val = (snow + rain)/2
    elif snow == 0.0:
        val = rain
    else:
        val = snow
    print("val is ", val)
        
    return val

def _process_packet(data):
    ev=aiobs.HCI_Event()
    xx = ev.decode(data)
    xx = EddyStone().decode(ev)
    if xx:
        if (xx['url'][:24] == "https://IoTexasSteps.com"):
                numOfSteps = xx['url'][25:]
                stepMessage = "IoTaxes steps:{}".format(numOfSteps)
                saveSteps(numOfSteps)
                print(stepMessage)
                event_loop.stop()
                btctrl.stop_scan_request()             
                
def saveSteps(num):
    global steps
    print("saveSteps()", num)
    steps = num

def getSteps():
    global steps
    print("getSteps()", steps)
    return steps

if __name__ == '__main__':
      mydev = 0
      event_loop = asyncio.get_event_loop()
      mysocket = aiobs.create_bt_socket(mydev)
      fac = event_loop._create_connection_transport(mysocket,aiobs.BLEScanRequester,None,None)
      conn,btctrl = event_loop.run_until_complete(fac)
      btctrl.process = _process_packet
      btctrl.send_scan_request()
      try:
          while True:
              btctrl.send_scan_request()
              event_loop.run_forever()
              client.loop_start()
              client.on_message = on_message
              client.subscribe(subscribetopic)
              time.sleep(5)
              client.loop_stop()
          
      except KeyboardInterrupt:
          print("keyboard interrupt")
      finally:
          print('closing event loop')
          btctrl.stop_scan_request()
          conn.close()
          event_loop.close()

#client.on_message = on_message
#client.loop_start()
#client.subscribe(subscribetopic)
#print("subscribed")
#time.sleep(15)
#client.loop_stop()
