export default {
  dev: {
    '/api/': {
      target: 'http://192.168.1.100:9080',
      changeOrigin: true,
    },
  },
};
