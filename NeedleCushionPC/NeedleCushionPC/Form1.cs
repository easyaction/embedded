using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace NeedleCushionPC
{
    public partial class Form1 : Form
    {
        private static readonly string BaseURL = "http://needle-cushion.appspot.com";
        private static readonly TimeSpan CheckInterval = TimeSpan.FromSeconds(10.0);

        private volatile bool _shouldStop = false;
        private Thread _workerThread;

        public Form1()
        {
            InitializeComponent();
        }

        private void Form1_Load(object sender, EventArgs e)
        {
            _workerThread = new Thread(MainLoop);
            _workerThread.Start();
        }

        private void Form1_KeyDown(object sender, KeyEventArgs e)
        {

        }

        private void MainLoop()
        {
            while (!_shouldStop)
            {
                try
                {
                    WebRequest request = WebRequest.CreateHttp(BaseURL + "/status");
                    WebResponse response = request.GetResponse();
                    Stream stream = response.GetResponseStream();
                    StreamReader reader = new StreamReader(stream);
                    string status = reader.ReadToEnd();
                    if (status == "locked")
                    {
                        Invoke((MethodInvoker)delegate { Show(); });
                    }
                    else if (status == "unlocked")
                    {
                        Invoke((MethodInvoker)delegate { Hide(); });
                    }
                    stream.Close();
                    reader.Close();
                    response.Close();
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
                Thread.Sleep(CheckInterval);
            }
        }
    }
}
