import paho.mqtt.client as mqtt
import time
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


wdf = pd.read_csv('steps-data-weather.csv')
###############NEW MODEL
def calculate_theta(num_days):
	num_days = num_days+1
	X = np.zeros((num_days,3))  #26 days data, 23 unique user id steps
	#wdf = pd.read_csv('steps-data-weather.csv')
	wea=np.array(wdf)
	#print (wea.shape)
	#Accumulate only the weather data from the array
	wea=wea[0:num_days,0:3]   #26 days data, column: high temp, low temp, precipitation
	wea = np.asfarray(wea, float)
	print ("I am weah shape", wea.shape)
	#replicate weather for all the steps data
	for x in range(1):
	    X[num_days*x:num_days*(x+1),:]=wea   
	ones_x = np.ones((num_days,1))
	
	#create the X_matrix with Ones in the first column followed by weather data in the next
	X_ = np.concatenate((ones_x,X),axis=1)
	
	#load the steps data
	
	steps = wdf.iloc[0:num_days,3]
	#print("This is steps: ",steps)
	steps=np.array(steps)
	#print ("This is ", steps.shape)
	#create a matrix of just steps data, removing the IDs
	y=steps[0:num_days]
	y=np.reshape(y,(y.shape[0],1))
	y = np.asfarray(y, float)
	#define learning rate
	alpha = 0.0001
	#define the total number of iterations 
	iters = 1000
	#define initial weights
	theta = np.array([[1725.18550495,41.73728844,16.29751196,553.50462998]])
	
	#compute the mean squared error
	def computecost(X,y,theta):
	    inner = np.power((np.matmul(X,theta.T)-y),2)
	    return np.sum(inner)/(2*len(X))
	
	result = computecost(X_,y,theta)
	#print(result)
	
	#compute the gradient and update the cost
	def gradientDescent(X,y,theta,alpha,iters):
	    for i  in range(iters):
              if num_days == 1:
                cost = computecost(X,y,theta)
                return (theta,cost)
              else:
                theta = theta - (alpha/len(X))*np.sum((X@theta.T-y)*X,axis=0)
                cost = computecost(X,y,theta)
                return (theta,cost)
	
	g,cost=gradientDescent(X_,y,theta,alpha,iters)
	return g[0]

def convert_steps(high_weather,low_weather,precipitation):
    b0,b1,b2,b3 = 1725.18550495,41.73728844,16.29751196,553.50462998
    value = b0 + b1 * high_weather + b2 * low_weather + b3 * precipitation
    return int(value)

def convert_steps_new(high_weather,low_weather,precipitation, days):
    b0,b1,b2,b3 = calculate_theta(days)
    value = b0 + b1 * high_weather + b2 * low_weather + b3 * precipitation
    return int(value)

def print_monthly():
  len_csv = wdf.shape[0]
  print(len_csv)
  for i in range(len_csv):
    high_weather, low_weather, precipitation = wdf.iloc[i,0:3]

    old_model_pred = convert_steps(high_weather, low_weather, precipitation)

    new_model_pred = convert_steps_new(high_weather, low_weather, precipitation, i)

    print("Day Number: ", i+1)
    print("old model prediction is: ", old_model_pred)
    print("new model prediction is: ", new_model_pred) 

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
  print("message received ", message)
  dict_weather = json.loads(message)
  
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

  print("User Model Step Count Data start") 
  
  print(userRec)
  client.publish(publishtopic, userRec)
  print("published")  
  time.sleep(5)
  
def on_message(client, userdata, message):

  global numOfSteps
  global stepMessage
  print("----------inside on message")

  check_message = message.payload.decode("utf-8")
  print("Message is ", check_message)
  if check_message != "month":
    steps_eval(client,userdata,check_message)
  else:
    print_monthly()

### put this code here to allow user to get steps first then check monthly goal
### by clicking the monthly goal button on the app
client.loop_start()
client.on_message = on_message
client.subscribe(subscribetopic)
print("subscribed")
time.sleep(10)
client.loop_stop()
###


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

