const axios = require('axios');
const orderModel = require('../models/ordermodel');
const orderItemModel = require('../models/orderItemmodel');
const applyModel = require('../models/applyModel');
const vehicleModel = require('../models/vehiclemodel');
const voucherModel = require('../models/voucherModel');

const SEPAY_API_KEY = process.env.SEPAY_API_KEY || 'YOUR_SEPAY_API_KEY_HERE';

// Sepay bank info (lấy từ Sepay account của bạn)
const BANK_INFO = {
  accountNo: process.env.BANK_ACCOUNT_NO || '9946311901',
  accountName: process.env.BANK_ACCOUNT_NAME || 'NGUYEN CONG VINH',
  bankId: process.env.BANK_ID || '970422', // MB Bank
  bankName: process.env.BANK_NAME || 'MB Bank'
};

const paymentController = {
  // Generate QR Code for payment
  generateQRCode: async (req, res) => {
    try {
      const { orderId, amount } = req.body;

      if (!orderId || !amount) {
        return res.status(400).json({
          success: false,
          message: 'Thiếu thông tin'
        });
      }

      // Tạo nội dung chuyển khoản với format: HOrderID{id}H
      const transferContent = `HOrderID${orderId}H`;

      // Tạo QR code URL với VietQR format
      const qrUrl = `https://img.vietqr.io/image/${BANK_INFO.bankId}-${BANK_INFO.accountNo}-compact.png?amount=${amount}&addInfo=${encodeURIComponent(transferContent)}&accountName=${encodeURIComponent(BANK_INFO.accountName)}`;

      res.json({
        success: true,
        data: {
          qrUrl: qrUrl,
          bankInfo: {
            bankName: BANK_INFO.bankName,
            accountNo: BANK_INFO.accountNo,
            accountName: BANK_INFO.accountName,
            amount: amount,
            content: transferContent
          },
          expireTime: new Date(Date.now() + 10 * 60 * 1000) // 10 minutes
        }
      });
    } catch (error) {
      console.error('Generate QR error:', error);
      res.status(500).json({
        success: false,
        message: 'Lỗi khi tạo mã QR'
      });
    }
  },

  // Get transactions from Sepay
  getTransactions: async (req, res) => {
    try {
      const response = await axios.get('https://my.sepay.vn/userapi/transactions/list', {
        headers: {
          Authorization: `Bearer ${SEPAY_API_KEY}`,
          'Content-Type': 'application/json'
        }
      });

      res.json({
        success: true,
        transactions: response.data.transactions || []
      });
    } catch (error) {
      console.error('Get transactions error:', error);
      res.status(500).json({
        success: false,
        message: 'Lỗi khi lấy danh sách giao dịch'
      });
    }
  },

  // Check payment status for specific order
  checkPaymentStatus: async (req, res) => {
    try {
      const { orderId } = req.params;

      // Kiểm tra order status
      const orderResult = await orderModel.readOrder('OrderID', orderId);
      
      // Handle null or empty result
      if (!orderResult) {
        return res.json({
          success: true,
          data: { status: 'NotFound' }
        });
      }

      // Convert to array if needed
      const orders = Array.isArray(orderResult) ? orderResult : [orderResult];
      
      if (orders.length === 0) {
        return res.json({
          success: true,
          data: { status: 'NotFound' }
        });
      }

      const orderStatus = orders[0].Status;
      
      if (orderStatus === 'Accepted') {
        res.json({
          success: true,
          data: { status: 'Paid' }
        });
      } else if (orderStatus === 'Cancel') {
        res.json({
          success: true,
          data: { status: 'Expired' }
        });
      } else {
        res.json({
          success: true,
          data: { status: 'Pending' }
        });
      }
    } catch (error) {
      console.error('Check payment status error:', error);
      res.status(500).json({
        success: false,
        message: 'Lỗi khi kiểm tra trạng thái thanh toán'
      });
    }
  }
};

// Helper function to extract OrderID from transaction content
function extractOrderID(content) {
  if (!content) return null;
  
  // Format: 'HOrderID12345H'
  const orderIDStart = content.indexOf('HOrderID');
  if (orderIDStart === -1) return null;
  
  const orderIDEnd = content.indexOf('H', orderIDStart + 8);
  if (orderIDEnd === -1) return null;
  
  const orderID = content.substring(orderIDStart + 8, orderIDEnd);
  return orderID;
}

// Check transactions and update order status
async function checkTransactions() {
  try {
    const response = await axios.get('https://my.sepay.vn/userapi/transactions/list', {
      headers: {
        Authorization: `Bearer ${SEPAY_API_KEY}`,
        'Content-Type': 'application/json'
      }
    });

    const transactions = response.data.transactions || [];
    let transactionAmountnID = [];

    for (const transaction of transactions) {
      const amount = transaction.amount_in;
      const orderID = extractOrderID(transaction.transaction_content) || '';
      
      if (orderID) {
        transactionAmountnID.push({ amount, orderID });
      }
    }

    console.log('Checked transactions:', transactionAmountnID.length, 'found');
    return transactionAmountnID;
  } catch (error) {
    console.error('Error fetching transactions:', error.message);
    return [];
  }
}

// Update order status based on transactions
async function updateOrderStatus() {
  try {
    const transactions = await checkTransactions();
    
    if (!transactions || transactions.length === 0) {
      console.log('No new transactions found');
      return;
    }

    // Get pending orders
    const ordersResult = await orderModel.readOrder('Status', 'Pending');
    
    // Make sure we have an array
    let orders = [];
    if (ordersResult) {
      orders = Array.isArray(ordersResult) ? ordersResult : [ordersResult];
    }

    if (orders.length === 0) {
      console.log('No pending orders');
      return;
    }

    console.log(`Checking ${orders.length} pending orders against ${transactions.length} transactions`);

    for (const order of orders) {
      const matchingTransaction = transactions.find(
        t => t.orderID == order.OrderID && t.amount == order.GrandTotal
      );

      if (matchingTransaction) {
        console.log(`🔄 Processing order ${order.OrderID} - Updating status to Accepted...`);
        
        // Update order status to Accepted
        await orderModel.updateOrderStatus(order.OrderID, 'Accepted');
        console.log(`✅ Order ${order.OrderID} status updated to Accepted`);
        
        // Decrease stock for all items in this order
        try {
          console.log(`📦 Getting order items for order ${order.OrderID}...`);
          const orderItems = await orderItemModel.getOrderItems(order.OrderID);
          console.log(`📦 Found ${orderItems.length} items in order ${order.OrderID}`);
          
          for (const item of orderItems) {
            if (item.VehicleID) {
              console.log(`⬇️ Decreasing stock for ${item.VehicleID} by ${item.Quantity}...`);
              await vehicleModel.decreaseStock(item.VehicleID, item.Quantity);
              console.log(`✅ Stock decreased for ${item.VehicleID}: -${item.Quantity}`);
            } else {
              console.log(`⚠️ Item has no VehicleID:`, item);
            }
          }
        } catch (stockError) {
          console.error(`❌ Error decreasing stock for order ${order.OrderID}:`, stockError.message);
          console.error('Stack trace:', stockError.stack);
        }

        // Decrease voucher quantity if voucher was applied
        try {
          console.log(`🎫 Checking for applied voucher on order ${order.OrderID}...`);
          const appliedVoucher = await applyModel.getAppliedVoucher(order.OrderID);
          console.log(`🎫 Applied voucher result:`, appliedVoucher);
          
          if (appliedVoucher && appliedVoucher.VoucherCode) {
            console.log(`⬇️ Decreasing voucher quantity for ${appliedVoucher.VoucherCode}...`);
            await voucherModel.decreaseQuantity(appliedVoucher.VoucherCode, 1);
            console.log(`✅ Voucher quantity decreased: ${appliedVoucher.VoucherCode}`);
          } else {
            console.log(`ℹ️ No voucher applied to order ${order.OrderID}`);
          }
        } catch (voucherError) {
          console.error(`❌ Error decreasing voucher for order ${order.OrderID}:`, voucherError.message);
          console.error('Stack trace:', voucherError.stack);
        }

        console.log(`✅ Order ${order.OrderID} confirmed - Amount: ${order.GrandTotal} VND`);
      }
    }
  } catch (error) {
    console.error('Error updating order status:', error.message);
  }
}

// Export functions
module.exports = paymentController;
module.exports.updateOrderStatus = updateOrderStatus;
module.exports.checkTransactions = checkTransactions;