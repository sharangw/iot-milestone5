import numpy as np
import pandas as pd
#define X size

wdf = pd.read_csv('steps-data-weather.csv')
wdf_days = len(wdf)

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

for i in range(wdf_days):
  	high_weather, low_weather, precipitation = wdf.iloc[i,0:3]
  	
  	old_model_pred = convert_steps(high_weather, low_weather, precipitation)
  	
  	new_model_pred = convert_steps_new(high_weather, low_weather, precipitation, i)
  
  	print("Day Number: ", i+1)
  	print("old model prediction is: ", old_model_pred)
  	print("new model prediction is: ", new_model_pred) 
  
