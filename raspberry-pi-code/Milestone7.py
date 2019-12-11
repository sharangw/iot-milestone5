import paho.mqtt.client as mqtt
import time
from datetime import datetime
import pytz
import pandas as pd
import numpy as np

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

def steps_eval(client, userdata, message):
  dict_weather = json.loads(message)
  
  # Steps today
  low = dict_weather["low"]
  high = dict_weather["high"]
  rain = dict_weather["rain"]
  snow = dict_weather["snow"]
  precipitation = convert_precip(snow,rain)
  steps_today = convert_steps(high,low,precipitation)
  print("Number of steps today is ",steps_today)
  
  actualSteps = getSteps()
  actualSteps = int(actualSteps) * 1000
  print("Actual Steps ", actualSteps)

  userRec = ""
  hourlySteps = calculateHourlyGoal(steps_today, actualSteps)
  if hourlySteps > 0:
    userRec = "You must walk {} steps this hour to keep up with your goal".format(hourlySteps)
  else:
    userRec = "You are on track to reach your step goal!" 
  
  print(userRec)
  
  stepsList = "{},{},{}".format(actualSteps,steps_today,hourlySteps)
  print(stepsList)
  client.publish(publishtopic, stepsList)
  print("published")  
  time.sleep(5)

def on_message(client, userdata, message):

  global numOfSteps
  global stepMessage
  print("----------inside on message")

  check_message = message.payload.decode("utf-8")
  print("Message is ", check_message)
  steps_eval(client,userdata,check_message)

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

def calculateHourlyGoal(expectedSteps, actualSteps):
  tzCentral = pytz.timezone('America/Virgin')
  print("steps to walk today: ", expectedSteps)
  now = datetime.now(tzCentral).strftime("%H")
  print("now: ", now)
  
  now = int(now)
  endtime = 24
  hoursLeft = endtime - now
  print("hoursLeft: ", hoursLeft)
  
  stepsToCover = expectedSteps - actualSteps
  if stepsToCover > 0:
    hourlySteps = (int) (stepsToCover/hoursLeft)
    return hourlySteps
  else:
    hourlySteps = 0
    return hourlySteps
                
def saveSteps(num):
    global steps
    print("saveSteps()", num)
    steps = num

def getSteps():
    global steps
    print("getSteps()", steps)
    return steps

if __name__ == '__main__':
      print('Inside Main')
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

