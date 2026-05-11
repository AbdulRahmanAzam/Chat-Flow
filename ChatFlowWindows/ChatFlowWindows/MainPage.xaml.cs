using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Devices.Enumeration;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;

namespace ChatFlowWindows;

public class BluetoothDeviceInfo
{
    public string Name { get; set; } = string.Empty;
    public string Id { get; set; } = string.Empty;
    public bool IsBLE { get; set; } = false;
}

public class ChatMessage
{
    public string Sender { get; set; } = string.Empty;
    public string Text { get; set; } = string.Empty;
}

public sealed partial class MainPage : Page, IDisposable
{
    private static readonly Guid ChatServiceGuid = Guid.Parse("E8F1787E-1243-4001-9050-05D97F9271BA");
    private const ushort CompanyId = 0x00FF; // Standard experimental ID

    private ObservableCollection<BluetoothDeviceInfo> _devices = new();
    private ObservableCollection<ChatMessage> _messages = new();
    
    private StreamSocket? _socket;
    private DataWriter? _writer;
    private DataReader? _reader;
    private StreamSocketListener? _listener;
    private RfcommServiceProvider? _provider;
    private CancellationTokenSource? _cts;

    // BLE components
    private BluetoothLEAdvertisementPublisher? _blePublisher;
    private BluetoothLEAdvertisementWatcher? _bleWatcher;

    public MainPage()
    {
        InitializeComponent();
        DeviceListView.ItemsSource = _devices;
        ChatListView.ItemsSource = _messages;
        
        this.Unloaded += (s, e) => Dispose();
        
        SetupBLE();
    }

    private void SetupBLE()
    {
        _bleWatcher = new BluetoothLEAdvertisementWatcher();
        // Relaxing filter to find the A32 mobile device
        _bleWatcher.Received += OnBLEAdvertisementReceived;
        
        _blePublisher = new BluetoothLEAdvertisementPublisher();
        _blePublisher.Advertisement.ServiceUuids.Add(ChatServiceGuid);
    }

    private async void ScanButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            StatusTextBlock.Text = "Status: Scanning (Classic + BLE)...";
            _devices.Clear();

            // 1. Classic Bluetooth Discovery (Paired)
            var selector = BluetoothDevice.GetDeviceSelector();
            var devices = await DeviceInformation.FindAllAsync(selector);
            foreach (var device in devices)
            {
                _devices.Add(new BluetoothDeviceInfo { Name = device.Name, Id = device.Id, IsBLE = false });
            }

            // 2. BLE Discovery (Unpaired)
            _bleWatcher?.Start();

            StatusTextBlock.Text = $"Status: Scanning. Found {_devices.Count} classic devices.";
        }
        catch (Exception ex)
        {
            StatusTextBlock.Text = $"Status: Scan Error: {ex.Message}";
        }
    }

    private void OnBLEAdvertisementReceived(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementReceivedEventArgs args)
    {
        string name = string.IsNullOrEmpty(args.Advertisement.LocalName) ? $"BLE Device {args.BluetoothAddress:X}" : args.Advertisement.LocalName;
        
        // Find our manufacturer data
        var manufacturerData = args.Advertisement.ManufacturerData.FirstOrDefault(m => m.CompanyId == CompanyId);
        
        DispatcherQueue.TryEnqueue(() => 
        {
            // Add to device list if not already there
            var existing = _devices.FirstOrDefault(d => d.Id == args.BluetoothAddress.ToString());
            if (existing == null)
            {
                _devices.Add(new BluetoothDeviceInfo 
                { 
                    Name = name, 
                    Id = args.BluetoothAddress.ToString(),
                    IsBLE = true 
                });

                // Detect A32
                if (name.Contains("A32", StringComparison.OrdinalIgnoreCase))
                {
                    StatusTextBlock.Text = $"Status: Found your A32 mobile! Select it to chat.";
                }
            }

            // If it contains a message, display it
            if (manufacturerData != null)
            {
                var reader = DataReader.FromBuffer(manufacturerData.Data);
                string text = reader.ReadString(manufacturerData.Data.Length);
                
                // Only add if it's a new message (BLE advertisements repeat frequently)
                var lastMsg = _messages.LastOrDefault(m => m.Sender == name + " (BLE)");
                if (lastMsg == null || lastMsg.Text != text)
                {
                    _messages.Add(new ChatMessage { Sender = name + " (BLE)", Text = text });
                }
            }
        });
    }

    private async void HostButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            CleanupConnection();
            
            _provider = await RfcommServiceProvider.CreateAsync(RfcommServiceId.FromUuid(ChatServiceGuid));
            
            _listener = new StreamSocketListener();
            _listener.ConnectionReceived += OnConnectionReceived;
            
            await _listener.BindServiceNameAsync(_provider.ServiceId.AsString(), SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication);
            
            _provider.StartAdvertising(_listener);
            
            StatusTextBlock.Text = "Status: Hosting. Waiting for connection...";
            HostButton.IsEnabled = false;
        }
        catch (Exception ex)
        {
            StatusTextBlock.Text = $"Status: Host Error: {ex.Message}";
        }
    }

    private async void OnConnectionReceived(StreamSocketListener sender, StreamSocketListenerConnectionReceivedEventArgs args)
    {
        _socket = args.Socket;
        await StartChatSession("Remote User");
    }

    private async void DeviceListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (DeviceListView.SelectedItem is BluetoothDeviceInfo info)
        {
            if (info.IsBLE)
            {
                StatusTextBlock.Text = $"Status: BLE Mode. Just type and send to broadcast.";
                return;
            }

            StatusTextBlock.Text = $"Status: Connecting to {info.Name}...";
            try
            {
                CleanupConnection();
                
                var device = await BluetoothDevice.FromIdAsync(info.Id);
                var services = await device.GetRfcommServicesForIdAsync(RfcommServiceId.FromUuid(ChatServiceGuid));

                if (services.Services.Count > 0)
                {
                    var service = services.Services[0];
                    _socket = new StreamSocket();
                    await _socket.ConnectAsync(service.ConnectionHostName, service.ConnectionServiceName);
                    await StartChatSession(info.Name);
                }
                else
                {
                    StatusTextBlock.Text = "Status: Service not found on device.";
                }
            }
            catch (Exception ex)
            {
                StatusTextBlock.Text = $"Status: Connect Error: {ex.Message}";
            }
        }
    }

    private async Task StartChatSession(string peerName)
    {
        if (_socket == null) return;
        
        _writer = new DataWriter(_socket.OutputStream);
        _reader = new DataReader(_socket.InputStream);
        _cts = new CancellationTokenSource();
        
        DispatcherQueue.TryEnqueue(() => 
        {
            StatusTextBlock.Text = $"Status: Connected to {peerName}";
            _messages.Add(new ChatMessage { Sender = "System", Text = $"Connected to {peerName}" });
            HostButton.IsEnabled = false;
        });

        await ReceiveMessages(_cts.Token);
    }

    private async Task ReceiveMessages(CancellationToken token)
    {
        if (_reader == null) return;
        try
        {
            while (!token.IsCancellationRequested)
            {
                uint sizeFieldCount = await _reader.LoadAsync(sizeof(uint)).AsTask(token);
                if (sizeFieldCount != sizeof(uint)) break;

                uint stringLength = _reader.ReadUInt32();
                uint actualStringLength = await _reader.LoadAsync(stringLength).AsTask(token);
                if (actualStringLength != stringLength) break;

                string text = _reader.ReadString(stringLength);
                DispatcherQueue.TryEnqueue(() => 
                {
                    _messages.Add(new ChatMessage { Sender = "Peer", Text = text });
                });
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception)
        {
            DispatcherQueue.TryEnqueue(() => 
            {
                StatusTextBlock.Text = "Status: Disconnected.";
                _messages.Add(new ChatMessage { Sender = "System", Text = "Connection lost." });
                HostButton.IsEnabled = true;
            });
        }
        finally
        {
            CleanupConnection();
        }
    }

    private async void SendButton_Click(object sender, RoutedEventArgs e)
    {
        await SendMessage();
    }

    private async void MessageTextBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter)
        {
            await SendMessage();
        }
    }

    private async Task SendMessage()
    {
        string text = MessageTextBox.Text;
        if (string.IsNullOrWhiteSpace(text)) return;

        // 1. Handle BLE Broadcast (No Connection Needed)
        if (DeviceListView.SelectedItem is BluetoothDeviceInfo info && info.IsBLE)
        {
            SendBLEBroadcast(text);
            _messages.Add(new ChatMessage { Sender = "Me (BLE)", Text = text });
            MessageTextBox.Text = "";
            return;
        }

        // 2. Handle Classic Connection
        if (_writer == null)
        {
            StatusTextBlock.Text = "Status: Not connected. Select a device or start hosting.";
            return;
        }

        try
        {
            _writer.WriteUInt32(_writer.MeasureString(text));
            _writer.WriteString(text);
            await _writer.StoreAsync();
            
            _messages.Add(new ChatMessage { Sender = "Me", Text = text });
            MessageTextBox.Text = "";
        }
        catch (Exception ex)
        {
            StatusTextBlock.Text = $"Status: Send Error: {ex.Message}";
            CleanupConnection();
        }
    }

    private void SendBLEBroadcast(string text)
    {
        if (_blePublisher == null) return;

        try
        {
            _blePublisher.Stop();
            _blePublisher.Advertisement.ManufacturerData.Clear();

            // Truncate to fit in BLE advertisement (max ~20-25 chars with headers)
            if (text.Length > 20) text = text.Substring(0, 20) + "...";

            var writer = new DataWriter();
            writer.WriteString(text);
            
            var manufacturerData = new BluetoothLEManufacturerData
            {
                CompanyId = CompanyId,
                Data = writer.DetachBuffer()
            };

            _blePublisher.Advertisement.ManufacturerData.Add(manufacturerData);
            _blePublisher.Start();
            
            StatusTextBlock.Text = $"Status: BLE Broadcast sent: {text}";
        }
        catch (Exception ex)
        {
            StatusTextBlock.Text = $"Status: BLE Error: {ex.Message}";
        }
    }

    private void CleanupConnection()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;

        _writer?.DetachStream();
        _writer?.Dispose();
        _writer = null;

        _reader?.DetachStream();
        _reader?.Dispose();
        _reader = null;

        _socket?.Dispose();
        _socket = null;

        _listener?.Dispose();
        _listener = null;

        _bleWatcher?.Stop();
        _blePublisher?.Stop();

        if (_provider != null)
        {
            _provider.StopAdvertising();
            _provider = null;
        }

        DispatcherQueue.TryEnqueue(() => 
        {
            HostButton.IsEnabled = true;
        });
    }

    public void Dispose()
    {
        CleanupConnection();
    }
}
