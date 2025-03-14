export default {
  dev: {
    '/api/': {
      target: 'http://172.21.0.239:9080',
      changeOrigin: true,
    },
  },
};
