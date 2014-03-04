import random
import unittest
import sys
import time
import types
from ossie.utils import sb

# add local build path to test out api, issue with bulkio.<library> and bulkio.bulkioInterfaces... __init__.py
# differs during build process
sys.path = [ '../../build/lib' ] + sys.path

import bulkio

def str_to_class(s):
    if s in globals() and isinstance(globals()[s], types.ClassType):
        return globals()[s]
    return None

class SRI_Tests(unittest.TestCase):
    def __init__(self, methodName='runTest'):
        unittest.TestCase.__init__(self, methodName)

    def setUp(self):
        self.seq = range(10)

    def test_create(self):
        sri = bulkio.sri.create()
        
        self.assertEqual( sri.hversion, 1, "Version Incompatable" )

if __name__ == '__main__':
    if len(sys.argv) < 2 :
        unittest.main()
    else:
        suite = unittest.TestLoader().loadTestsFromTestCase(globals()[sys.argv[1]] ) 
        unittest.TextTestRunner(verbosity=2).run(suite)

##python -m unittest test_module1 test_module2
##python -m unittest test_module.TestClass
##python -m unittest test_module.TestClass.test_method
##You can pass in a list with any combination of module names, and fully qualified class or method names.
##You can run tests with more detail (higher verbosity) by passing in the -v flag:
##python -m unittest -v test_module
##For a list of all the command-line options:
##python -m unittest -h

