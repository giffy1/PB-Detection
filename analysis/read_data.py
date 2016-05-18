# -*- coding: utf-8 -*-
"""
Created on Thu May 12 15:04:33 2016

@author: snoran
"""

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

fname = "../data/sample/RSSI.csv"
rssi = pd.read_csv(fname,sep=',').values

plt.plot(rssi[:,1])
plt.show()

freqInMHz = 2462
#levelInDb = -83
SNR = -87

result = (27.55 - (20 * np.log10(freqInMHz)) + np.fabs(rssi)) / 20.0
meters = np.power(10, result)

feet = meters * 3.2808